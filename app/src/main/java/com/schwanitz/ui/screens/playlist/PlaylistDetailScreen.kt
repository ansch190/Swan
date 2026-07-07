package com.schwanitz.ui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import com.schwanitz.domain.model.Song
import com.schwanitz.ui.components.SongListItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onNavigateBack: () -> Unit,
    onAddSongsClick: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    val playlistName by viewModel.playlistName.collectAsState()
    val songs by viewModel.songs.collectAsState()
    var localSongs by remember { mutableStateOf(songs) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var pendingDeletions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var songToRemove by remember { mutableStateOf<Song?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(playlistName) { mutableStateOf(playlistName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showRenameDialog) {
        if (showRenameDialog) {
            focusRequester.requestFocus()
        }
    }

    songToRemove?.let { song ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text("Song entfernen") },
            text = { Text("Â»${song.title}Â« aus Playlist entfernen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        localSongs = localSongs.filter { it.id != song.id }
                        pendingDeletions = pendingDeletions + song.id
                        songToRemove = null
                    }
                ) {
                    Text("Entfernen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    LaunchedEffect(songs) {
        localSongs = songs
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (!isEditing) return@rememberReorderableLazyListState
        localSongs = localSongs.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = playlistName,
                    modifier = if (!isEditing) Modifier.clickable { showRenameDialog = true } else Modifier
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (isEditing) {
                    IconButton(onClick = {
                        viewModel.savePlaylistChanges(
                            songIds = localSongs.map { it.id },
                            deleteSongIds = pendingDeletions.toList()
                        )
                        pendingDeletions = emptySet()
                        isEditing = false
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Fertig")
                    }
                    IconButton(onClick = onAddSongsClick) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Songs hinzufÃ¼gen")
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten")
                    }
                }
            }
        )

        if (localSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Playlist is empty",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(localSongs, key = { it.id }) { song ->
                    if (isEditing) {
                        ReorderableItem(reorderableState, key = song.id) { isDragging ->
                            val alpha = if (isDragging) 0.7f else 1f
                            val dragHandle = Modifier.draggableHandle()
                            SongListItem(
                                song = song,
                                onClick = { viewModel.playSong(song) },
                                showDragHandle = true,
                                onRemoveClick = { songToRemove = song },
                                dragHandleModifier = dragHandle,
                                modifier = Modifier.alpha(alpha)
                            )
                        }
                    } else {
                        SongListItem(
                            song = song,
                            onClick = { viewModel.playSong(song) },
                            onFavoriteClick = { viewModel.toggleFavorite(song) },
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Playlist umbenennen") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renamePlaylist(renameText)
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
