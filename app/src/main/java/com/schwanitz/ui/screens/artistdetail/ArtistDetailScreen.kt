package com.schwanitz.ui.screens.artistdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.domain.model.Song
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.SongListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    onNavigateBack: () -> Unit,
    onAlbumClick: (String, String) -> Unit,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(artistName) {
        viewModel.loadArtistByName(artistName)
    }

    val songs by viewModel.songs.collectAsState()
    val sortedSongs = remember(songs) { songs.sortedBy { it.title } }
    val albums by viewModel.albums.collectAsState()
    val artistImageUri by viewModel.artistImageUri.collectAsState()
    val artistBiography by viewModel.artistBiography.collectAsState()
    var showBioDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.artist_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (artistBiography != null) {
                    IconButton(onClick = { showBioDialog = true }) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.cd_biography))
                    }
                }
            }
        )

        ArtistHeader(artistName = artistName, imageUri = artistImageUri)

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
                    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sortedSongs) { song ->
                            Box {
                                SongListItem(
                                    song = song,
                                    onClick = { viewModel.playSong(song) },
                                    onLongClick = { contextMenuSong = song }
                                )
                                DropdownMenu(
                                    expanded = contextMenuSong?.id == song.id,
                                    onDismissRequest = { contextMenuSong = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.context_play_all)) },
                                        onClick = {
                                            contextMenuSong = null
                                            viewModel.playAllFromSong(song)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(albums) { album ->
                            AlbumListItem(
                                albumName = album.name,
                                albumArtUri = album.albumArtUri,
                                onClick = { onAlbumClick(album.name, artistName) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBioDialog && artistBiography != null) {
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text(stringResource(R.string.artist_biography_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = artistBiography!!)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.source_format, "Last.fm"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBioDialog = false }) {
                    Text(stringResource(R.string.artist_biography_dismiss))
                }
            }
        )
    }
}

@Composable
private fun ArtistHeader(artistName: String, imageUri: String?) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = stringResource(R.string.cd_artist_photo),
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(100.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = stringResource(R.string.cd_artist_photo),
                        modifier = Modifier.size(100.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MarqueeText(
            text = if (artistName.isBlank()) stringResource(R.string.artist_no_artist) else artistName,
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
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
