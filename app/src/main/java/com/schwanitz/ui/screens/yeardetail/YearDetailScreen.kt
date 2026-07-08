package com.schwanitz.ui.screens.yeardetail

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarToday
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
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.SongListItem
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
    val albums by viewModel.albums.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Year Detail") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        YearHeader(year = year)

        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(selected = pagerState.currentPage == 0, onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            }) {
                Text("Songs", modifier = Modifier.padding(12.dp))
            }
            Tab(selected = pagerState.currentPage == 1, onClick = {
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            }) {
                Text("Albums", modifier = Modifier.padding(12.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(songs) { song ->
                            SongListItem(
                                song = song,
                                onClick = { viewModel.playSong(song, songs) }
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
                                onClick = { onAlbumClick(album.name, "") } // Artist empty for year view
                            )
                        }
                    }
                }
            }
        }
    }
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
                    contentDescription = "Year Photo",
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

@Composable
private fun AlbumListItem(albumName: String, albumArtUri: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(albumName) },
        leadingContent = {
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = "Album Art",
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
