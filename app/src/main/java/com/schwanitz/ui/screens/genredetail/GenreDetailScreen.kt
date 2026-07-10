package com.schwanitz.ui.screens.genredetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
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
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.SongListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genre: String,
    onNavigateBack: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String, String) -> Unit,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(genre) {
        viewModel.loadGenre(genre)
    }

    val songs by viewModel.songs.collectAsState()
    val sortedSongs = remember(songs) { songs.sortedBy { it.title } }
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val artistImageUris by viewModel.artistImageUris.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.genre_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        GenreHeader(genre = genre)

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
                            SongListItem(
                                song = song,
                                onClick = { viewModel.playSong(song, sortedSongs) }
                            )
                        }
                    }
                }
                1 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(artists) { artist ->
                            ArtistListItem(
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
                                onClick = { onAlbumClick(album.name, "") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreHeader(genre: String) {
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
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = stringResource(R.string.cd_genre_icon),
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MarqueeText(
            text = genre.ifBlank { "-" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
    }
}

@Composable
private fun ArtistListItem(artistName: String, imageUri: String?, onClick: () -> Unit) {
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
private fun AlbumListItem(albumName: String, albumArtUri: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(if (albumName.isBlank()) stringResource(R.string.album_no_album) else albumName) },
        leadingContent = {
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = stringResource(R.string.cd_album_art),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Album,
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
