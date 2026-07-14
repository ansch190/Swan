package com.schwanitz.ui.screens.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.schwanitz.R
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState
import com.schwanitz.ui.navigation.LocalBottomBarHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistListScreen(
    onPlaylistClick: (Long) -> Unit,
    viewModel: PlaylistListViewModel = hiltViewModel()
) {
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<PlaylistListItemData?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showDialog) {
        if (showDialog) {
            focusRequester.requestFocus()
        }
    }

    var showExportFormat by remember { mutableStateOf(false) }
    var playlistToExport by remember { mutableStateOf<PlaylistListItemData?>(null) }
    var pendingExportFormat by remember { mutableStateOf(PlaylistListViewModel.PlaylistExportFormat.M3U) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && playlistToExport != null) {
            showExportFormat = false
            coroutineScope.launch {
                try {
                    val content = viewModel.getPlaylistExportContent(
                        playlistToExport!!.id,
                        playlistToExport!!.name,
                        pendingExportFormat
                    )
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, context.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { }
            }
        }
    }

    if (showExportFormat) {
        BackHandler { showExportFormat = false }

        ExportFormatSelectionContent(
            onBack = { showExportFormat = false },
            onFormatSelected = { format ->
                pendingExportFormat = format
                playlistToExport?.let { p ->
                    exportLauncher.launch("${p.name}.${format.extension}")
                }
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.playlist_list_title)) },
                actions = {
                    IconButton(onClick = { showDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_create_playlist))
                    }
                }
            )

            if (uiState.playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.playlist_list_empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomBarHeight.current)
                ) {
                    items(uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistListItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) },
                            onExport = {
                                playlistToExport = playlist
                                showExportFormat = true
                            },
                            onDelete = if (!playlist.isFavorite) {{ playlistToDelete = playlist }} else null
                        )
                    }
                }
            }
        }
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.playlist_detail_delete_title)) },
            text = { Text(stringResource(R.string.playlist_detail_delete_message, playlist.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        playlistToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; newPlaylistName = "" },
            title = { Text(stringResource(R.string.playlist_create_title)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            showDialog = false
                            newPlaylistName = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; newPlaylistName = "" }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaylistListItem(
    playlist: PlaylistListItemData,
    onClick: () -> Unit,
    onExport: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = if (playlist.isFavorite) Icons.Filled.Favorite else Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (playlist.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(playlist.name) },
        supportingContent = {
            Text(stringResource(R.string.playlist_song_count, playlist.songCount))
        },
        trailingContent = {
            if (onExport != null || onDelete != null) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = null
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export)) },
                            onClick = { showMenu = false; onExport?.invoke() }
                        )
                        if (onDelete != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; onDelete() }
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatSelectionContent(
    onBack: () -> Unit,
    onFormatSelected: (PlaylistListViewModel.PlaylistExportFormat) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.export_choose_format)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaylistListViewModel.PlaylistExportFormat.entries.forEach { format ->
                ExportFormatCard(
                    icon = format.toIcon(),
                    title = stringResource(format.labelRes),
                    description = stringResource(format.descriptionRes),
                    onClick = { onFormatSelected(format) }
                )
            }
        }
    }
}

private fun PlaylistListViewModel.PlaylistExportFormat.toIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    PlaylistListViewModel.PlaylistExportFormat.M3U -> Icons.Filled.PlaylistPlay
    PlaylistListViewModel.PlaylistExportFormat.PLS -> Icons.Filled.List
    PlaylistListViewModel.PlaylistExportFormat.XSPF -> Icons.Filled.Code
    PlaylistListViewModel.PlaylistExportFormat.WPL -> Icons.Filled.DesktopWindows
    PlaylistListViewModel.PlaylistExportFormat.ASX -> Icons.Filled.VideoFile
    PlaylistListViewModel.PlaylistExportFormat.B4S -> Icons.Filled.FileCopy
}

@Composable
private fun ExportFormatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
