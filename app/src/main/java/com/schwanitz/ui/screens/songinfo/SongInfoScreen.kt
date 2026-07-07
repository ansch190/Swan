package com.schwanitz.ui.screens.songinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import coil.compose.AsyncImage
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork
import com.schwanitz.ui.components.MarqueeText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoScreen(
    songId: String,
    onNavigateBack: () -> Unit,
    viewModel: SongInfoViewModel = hiltViewModel()
) {
    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }

    val song by viewModel.song.collectAsState()
    val sourceName by viewModel.sourceName.collectAsState()
    val artworks by viewModel.artworks.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Info") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (song == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            SongHeader(song = song!!, artworks = artworks)

            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(selected = pagerState.currentPage == 0, onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                }) {
                    Text("Metadata", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = pagerState.currentPage == 1, onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }) {
                    Text("Technical", modifier = Modifier.padding(12.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> MetadataTab(song = song!!)
                    1 -> TechnicalTab(song = song!!, sourceName = sourceName)
                }
            }
        }
    }
}

@Composable
private fun SongHeader(song: Song, artworks: List<SongArtwork>) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (artworks.isNotEmpty()) {
            val artPagerState = rememberPagerState(pageCount = { artworks.size })
            LaunchedEffect(song.id) {
                artPagerState.scrollToPage(0)
            }
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
                        model = artworks[page].uri,
                        contentDescription = "Album Art",
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
        } else if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Fit
            )
        } else {
            Surface(
                modifier = Modifier.size(200.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Album Art",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MarqueeText(
            text = song.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = song.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song.album,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()
    }
}

@Composable
private fun MetadataTab(song: Song) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow("Title", song.title)
        InfoRow("Artist", song.artist)
        InfoRow("Album", song.album)
        InfoRow("Album Artist", song.albumArtist.ifBlank { "-" })
        InfoRow("Track", song.trackRaw.ifBlank { "-" })
        InfoRow("Disc", song.discRaw.ifBlank { "-" })
        InfoRow("Year", if (song.year > 0) song.year.toString() else "-")
        InfoRow("Genre", song.genre.ifBlank { "-" })
    }
}

@Composable
private fun TechnicalTab(song: Song, sourceName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow("Source", sourceName)
        InfoRow("Path", cleanPath(song.filePath))
        InfoRow("Filename", Uri.decode(song.filePath).substringAfterLast('/').substringBefore('?'))

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        InfoRow("Size", if (song.fileSize > 0L) formatFileSize(song.fileSize) else "-")
        InfoRow("Audio Codec", song.mimeType.ifBlank { "-" })
        InfoRow("Sample Rate", if (song.sampleRate > 0) "${song.sampleRate} Hz" else "-")
        InfoRow("Bitrate", if (song.bitrate > 0) "${song.bitrate} bps" else "-")
        InfoRow("Tag Version", song.tagVersion.ifBlank { "-" })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun cleanPath(path: String): String {
    val decoded = Uri.decode(path)
        .removePrefix("content://")
        .removePrefix("file://")
    val afterPrimary = decoded.substringAfterLast("primary:")
    val meaningful = if (afterPrimary != decoded) afterPrimary else decoded
    return meaningful.trimStart('/').substringBeforeLast('/').trimEnd('/')
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
