package com.schwanitz.ui.screens.yeardetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.schwanitz.R
import com.schwanitz.ui.components.AlbumListItem
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.SelectableSongItem
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearDetailScreen(
    year: Int,
    onNavigateBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String, String, Int) -> Unit,
    onAllYearsClick: () -> Unit = {},
    onAddToPlaylist: (String) -> Unit = {},
    viewModel: YearDetailViewModel = hiltViewModel()
) {
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)

    LaunchedEffect(year) {
        viewModel.loadYear(year)
    }

    val songs by viewModel.songs.collectAsState()
    val sortedSongs = remember(songs) { songs.sortedBy { it.title } }
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val artistImageUris by viewModel.artistImageUris.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val isSelecting by viewModel.isSelecting.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.year_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = onAllYearsClick) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = stringResource(R.string.section_years)
                    )
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
                Text(stringResource(R.string.section_artists), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = pagerState.currentPage == 2, onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(2) }
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
                                onAddToPlaylist = { onAddToPlaylist(viewModel.getSelectedSongIds()) },
                                onAddToQueue = { viewModel.addSelectionToQueue() }
                            )
                        }
                    }
                }
                1 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artists) { artist ->
                            YearArtistListItem(
                                artistName = artist,
                                imageUri = artistImageUris[artist],
                                onClick = { onArtistClick(artist) }
                            )
                        }
                    }
                }
                2 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(albums) { album ->
                            AlbumListItem(
                                albumName = album.name,
                                albumArtUri = album.albumArtUri,
                                onClick = { onAlbumClick(album.name, album.albumArtist, album.year) },
                                albumArtist = album.albumArtist
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearArtistListItem(artistName: String, imageUri: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(if (artistName.isBlank()) stringResource(R.string.artist_no_artist) else artistName) },
        leadingContent = {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.cd_artist_photo),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
