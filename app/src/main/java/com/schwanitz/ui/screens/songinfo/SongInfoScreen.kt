package com.schwanitz.ui.screens.songinfo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
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
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.ui.components.MarqueeText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoScreen(
    songId: String,
    onNavigateBack: () -> Unit,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String) -> Unit,
    onYearClick: (Int) -> Unit,
    onGenreClick: (String) -> Unit,
    viewModel: SongInfoViewModel = hiltViewModel()
) {
    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }

    val song by viewModel.song.collectAsState()
    val sourceName by viewModel.sourceName.collectAsState()
    val artworks by viewModel.artworks.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    var showLyricsDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.songinfo_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (lyrics != null) {
                    IconButton(onClick = { showLyricsDialog = true }) {
                        Icon(
                            Icons.Filled.Article,
                            contentDescription = stringResource(R.string.cd_lyrics)
                        )
                    }
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
                    Text(stringResource(R.string.songinfo_metadata_tab), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = pagerState.currentPage == 1, onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }) {
                    Text(stringResource(R.string.songinfo_technical_tab), modifier = Modifier.padding(12.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> MetadataTab(
                        song = song!!,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onYearClick = onYearClick,
                        onGenreClick = onGenreClick
                    )
                    1 -> TechnicalTab(song = song!!, sourceName = sourceName)
                }
            }
        }
    }

    if (showLyricsDialog && lyrics != null) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text(stringResource(R.string.songinfo_lyrics_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = lyrics!!)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.source_format, "Genius"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLyricsDialog = false }) {
                    Text(stringResource(R.string.songinfo_lyrics_dismiss))
                }
            }
        )
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
        } else if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = stringResource(R.string.cd_album_art),
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
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        MarqueeText(
            text = song.title.ifBlank { "-" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = song.artist.ifBlank { "-" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song.album.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()
    }
}

@Composable
private fun MetadataTab(
    song: Song,
    onAlbumClick: (String, String) -> Unit,
    onArtistClick: (String) -> Unit,
    onYearClick: (Int) -> Unit,
    onGenreClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow(stringResource(R.string.songinfo_field_title), song.title.ifBlank { "-" })
        InfoRow(
            label = stringResource(R.string.songinfo_field_artist),
            value = song.artist.ifBlank { "-" },
            onClick = { onArtistClick(song.artist) }
        )
        InfoRow(
            label = stringResource(R.string.songinfo_field_album),
            value = song.album.ifBlank { "-" },
            onClick = {
                onAlbumClick(song.album, song.artist)
            }
        )
        InfoRow(stringResource(R.string.songinfo_field_album_artist), song.albumArtist.ifBlank { "-" })
        InfoRow(stringResource(R.string.songinfo_field_track), song.trackRaw.ifBlank { "-" })
        InfoRow(stringResource(R.string.songinfo_field_disc), song.discRaw.ifBlank { "-" })
        InfoRow(
            label = stringResource(R.string.songinfo_field_year),
            value = if (song.year > 0) song.year.toString() else "-",
            onClick = if (song.year > 0) { { onYearClick(song.year) } } else null
        )
        InfoRow(
            label = stringResource(R.string.songinfo_field_genre),
            value = song.genre.ifBlank { "-" },
            onClick = if (song.genre.isNotBlank()) { { onGenreClick(song.genre) } } else null
        )
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

        InfoRow(stringResource(R.string.songinfo_field_source), sourceName)
        InfoRow(stringResource(R.string.songinfo_field_path), cleanPath(song.filePath))
        InfoRow(stringResource(R.string.songinfo_field_filename), Uri.decode(song.filePath).substringAfterLast('/').substringBefore('?'))

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        InfoRow(stringResource(R.string.songinfo_field_size), if (song.fileSize > 0L) formatFileSize(song.fileSize) else "-")
        InfoRow(stringResource(R.string.songinfo_field_audio_codec), song.mimeType.ifBlank { "-" })
        InfoRow(stringResource(R.string.songinfo_field_sample_rate), if (song.sampleRate > 0) stringResource(R.string.songinfo_sample_rate_hz, song.sampleRate.toString()) else "-")
        InfoRow(stringResource(R.string.songinfo_field_bitrate), if (song.bitrate > 0) stringResource(R.string.songinfo_bitrate_bps, song.bitrate.toString()) else "-")
        InfoRow(stringResource(R.string.songinfo_field_tag_version), song.tagVersion.ifBlank { "-" })
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = stringResource(R.string.songinfo_label_format, label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
