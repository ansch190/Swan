package com.schwanitz.ui.screens.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.PlayerControlBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    var showQueue by rememberSaveable { mutableStateOf(true) }
    var showLyricsDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nowplaying_title)) },
            actions = {
                if (lyrics != null) {
                    IconButton(onClick = { showLyricsDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = stringResource(R.string.cd_lyrics)
                        )
                    }
                }
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = if (showQueue) Icons.Filled.Image else Icons.Filled.List,
                        contentDescription = if (showQueue) stringResource(R.string.cd_show_album_art) else stringResource(R.string.cd_show_queue)
                    )
                }
            }
        )

        val currentSong = playerState.currentSong
        val artworks by viewModel.artworks.collectAsState()
        if (currentSong != null) {
            LaunchedEffect(currentSong.id) {
                viewModel.loadArtworks(currentSong.id)
                viewModel.loadLyrics(currentSong.id, currentSong.title, currentSong.artist)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!showQueue) {
                        if (artworks.isNotEmpty()) {
                            val artPagerState = rememberPagerState(pageCount = { artworks.size })
                            LaunchedEffect(currentSong.id) {
                                artPagerState.scrollToPage(0)
                            }
                            HorizontalPager(
                                state = artPagerState,
                                modifier = Modifier.size(280.dp)
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
                        } else if (currentSong.albumArtUri != null) {
                            AsyncImage(
                                model = currentSong.albumArtUri,
                                contentDescription = stringResource(R.string.cd_album_art),
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentScale = ContentScale.Fit
                            )
        } else {
                            Surface(
                                modifier = Modifier.size(280.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = stringResource(R.string.cd_album_art),
                                        modifier = Modifier.size(96.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    MarqueeText(
                        text = currentSong.title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentSong.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = currentSong.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                PlayerControlBar(
                    playerState = playerState,
                    onPlayPause = { viewModel.playerManager.togglePlayPause() },
                    onSkipNext = { viewModel.playerManager.skipToNext() },
                    onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
                    onShuffle = { viewModel.playerManager.toggleShuffle() },
                    onRepeat = { viewModel.playerManager.cycleRepeatMode() },
                    onSeek = { viewModel.playerManager.seekTo(it) }
                )

                val queue = playerState.queue
                val currentIdx = playerState.currentIndex

                AnimatedVisibility(
                    visible = showQueue && queue.isNotEmpty(),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                        Text(
                            text = stringResource(R.string.nowplaying_queue_header),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val lazyListState = rememberLazyListState()

                        LaunchedEffect(currentIdx) {
                            if (currentIdx >= 0) lazyListState.animateScrollToItem(currentIdx)
                        }

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.heightIn(max = 340.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            itemsIndexed(queue) { index, song ->
                                val isCurrent = index == currentIdx
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.playerManager.playFromIndex(index) }
                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrent) Icons.Filled.PlayArrow
                                                              else Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                MarqueeText(
                                                    text = song.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Text(
                                                    text = song.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                                                Icon(
                                                    imageVector = if (song.id in favoriteIds) Icons.Filled.Favorite
                                                                  else Icons.Filled.FavoriteBorder,
                                                    contentDescription = if (song.id in favoriteIds) stringResource(R.string.cd_remove_from_favorites)
                                                                        else stringResource(R.string.cd_add_to_favorites),
                                                    tint = if (song.id in favoriteIds) MaterialTheme.colorScheme.primary
                                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val currentLyrics = lyrics
            if (showLyricsDialog && currentLyrics != null) {
                AlertDialog(
                    onDismissRequest = { showLyricsDialog = false },
                    title = { Text(text = stringResource(R.string.songinfo_lyrics_title)) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = currentLyrics,
                                style = MaterialTheme.typography.bodyLarge
                            )
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
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.nowplaying_empty_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.nowplaying_empty_desc),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
