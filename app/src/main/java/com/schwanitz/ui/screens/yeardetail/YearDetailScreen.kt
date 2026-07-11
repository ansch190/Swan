package com.schwanitz.ui.screens.yeardetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.ui.components.AlbumListItem
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.PlaylistPickerDialog
import com.schwanitz.ui.components.SelectableSongItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearDetailScreen(
    year: Int,
    onNavigateBack: () -> Unit,
    onAlbumClick: (String, String) -> Unit,
    viewModel: YearDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(year) {
        viewModel.loadYear(year)
    }

    val songs by viewModel.songs.collectAsState()
    val sortedSongs = remember(songs) { songs.sortedBy { it.title } }
    val albums by viewModel.albums.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val isSelecting by viewModel.isSelecting.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    var showPlaylistPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.year_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        YearHeader(year = year)

        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(selected = pagerState.currentPage == 0, onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            }) {
                Text(stringResource(R.string.section_songs), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = pagerState.currentPage == 1, onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            }) {
                Text(stringResource(R.string.section_albums), modifier = Modifier.padding(12.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sortedSongs) { song ->
                            SelectableSongItem(
                                song = song,
                                isSelecting = isSelecting,
                                isSelected = song.id in selectedSongIds,
                                onSongClick = { viewModel.playSong(song) },
                                onToggleSelection = { viewModel.toggleSelection(song.id) },
                                onEnterSelection = { viewModel.enterSelection(song) },
                                onPlayAll = { viewModel.playAllFromSong(song) },
                                onPlaySelection = { viewModel.playSelection() },
                                onAddToPlaylist = { showPlaylistPicker = true },
                                onAddToQueue = { viewModel.addSelectionToQueue() }
                            )
                        }
                    }
                }
                1 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(albums) { album ->
                            AlbumListItem(
                                albumName = album.name,
                                albumArtUri = album.albumArtUri,
                                onClick = { onAlbumClick(album.name, album.albumArtist) }
                            )
                        }
                    }
                }
            }
        }
    }

    PlaylistPickerDialog(
        show = showPlaylistPicker,
        playlists = playlists,
        onDismiss = { showPlaylistPicker = false },
        onPlaylistSelected = { viewModel.addSelectionToPlaylist(it) }
    )
}

@Composable
private fun YearHeader(year: Int) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.CalendarToday,
                    contentDescription = stringResource(R.string.cd_year_photo),
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MarqueeText(
            text = if (year > 0) year.toString() else "-",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
    }
}
