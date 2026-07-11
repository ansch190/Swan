package com.schwanitz.ui.screens.albumdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.SongListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    albumArtistName: String,
    onNavigateBack: () -> Unit,
    onSeriesClick: (String) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(albumName, albumArtistName) {
        viewModel.loadAlbum(albumName, albumArtistName)
    }

    val songs by viewModel.songs.collectAsState()
    val artworks by viewModel.artworks.collectAsState()
    val series by viewModel.series.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isSelecting by viewModel.isSelecting.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val songsByCd = remember(songs) {
        songs.groupBy { it.discNumber.coerceAtLeast(1) }.toSortedMap()
    }
    val cdList = songsByCd.keys.toList()
    val pagerState = rememberPagerState(pageCount = { cdList.size.coerceAtLeast(1) })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.album_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                series?.let { s ->
                    IconButton(onClick = { onSeriesClick(s.name) }) {
                        Icon(
                            painter = painterResource(R.drawable.album_series),
                            contentDescription = stringResource(R.string.cd_series_icon)
                        )
                    }
                }
            }
        )

        AlbumHeader(albumName = albumName, artworks = artworks)

        if (cdList.isNotEmpty()) {
            if (cdList.size > 1) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    cdList.forEachIndexed { index, cdNumber ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            }
                        ) {
                            Text(stringResource(R.string.album_cd_format, cdNumber), modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val cdSongs = songsByCd[cdList[page]] ?: emptyList()
                var contextMenuSong by remember { mutableStateOf<Song?>(null) }
                var selectionContextMenuSong by remember { mutableStateOf<Song?>(null) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(cdSongs) { song ->
                        val isSelected = song.id in selectedSongIds
                        Box {
                            SongListItem(
                                song = song,
                                onClick = {
                                    if (isSelecting) viewModel.toggleSelection(song.id)
                                    else viewModel.playSong(song)
                                },
                                onLongClick = {
                                    if (isSelecting && isSelected) selectionContextMenuSong = song
                                    else contextMenuSong = song
                                },
                                selected = isSelecting && isSelected
                            )
                            DropdownMenu(
                                expanded = contextMenuSong?.id == song.id,
                                onDismissRequest = { contextMenuSong = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.context_selection)) },
                                    onClick = {
                                        contextMenuSong = null
                                        viewModel.enterSelection(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.context_play_all)) },
                                    onClick = {
                                        contextMenuSong = null
                                        viewModel.playAllFromSong(song, cdSongs)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.context_play_entire_album)) },
                                    onClick = {
                                        contextMenuSong = null
                                        viewModel.playEntireAlbum(song)
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = selectionContextMenuSong?.id == song.id,
                                onDismissRequest = { selectionContextMenuSong = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.context_selection_play)) },
                                    onClick = {
                                        selectionContextMenuSong = null
                                        viewModel.playSelection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.context_add_to_playlist)) },
                                    onClick = {
                                        selectionContextMenuSong = null
                                        showPlaylistPicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.context_add_to_queue)) },
                                    onClick = {
                                        selectionContextMenuSong = null
                                        viewModel.addSelectionToQueue()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (showPlaylistPicker) {
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text(stringResource(R.string.playlist_picker_title)) },
            text = {
                LazyColumn {
                    items(playlists) { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            modifier = Modifier.clickable {
                                showPlaylistPicker = false
                                viewModel.addSelectionToPlaylist(playlist.id)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AlbumHeader(albumName: String, artworks: List<AlbumArtwork>) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (artworks.isNotEmpty()) {
            val artPagerState = rememberPagerState(pageCount = { artworks.size })
            HorizontalPager(
                state = artPagerState,
                modifier = Modifier.size(200.dp)
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = artworks[page].uriLarge,
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            if (artworks.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(artworks.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (artPagerState.currentPage == index) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (artPagerState.currentPage == index)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier.size(200.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MarqueeText(
            text = if (albumName.isBlank()) stringResource(R.string.album_no_album) else albumName,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()
    }
}
