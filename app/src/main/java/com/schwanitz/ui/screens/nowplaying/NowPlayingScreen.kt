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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
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
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import com.schwanitz.ui.components.MarqueeText
import com.schwanitz.ui.components.PlayerControlBar
import com.schwanitz.ui.components.ArtworkPager
import com.schwanitz.ui.components.AlbumArtPlaceholder
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onSongInfoClick: (String) -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)
    var showQueue by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nowplaying_title)) },
            actions = {
                val currentSong = playerState.currentSong
                if (currentSong != null) {
                    IconButton(onClick = { onSongInfoClick(currentSong.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.songinfo_title)
                        )
                    }
                }
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = if (showQueue) Icons.Filled.Image else Icons.AutoMirrored.Filled.List,
                        contentDescription = if (showQueue) stringResource(R.string.cd_show_album_art) else stringResource(R.string.cd_show_queue)
                    )
                }
            }
        )

        val currentSong = playerState.currentSong
        if (currentSong != null) {
            val artworks by viewModel.artworks.collectAsState()
            LaunchedEffect(currentSong.id) {
                viewModel.loadArtworks(currentSong.albumId)
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
                        AlbumArtSection(
                            currentSong = currentSong,
                            artworks = artworks
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    SongInfoSection(song = currentSong)
                }
            }

            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                PlayerControlBar(
                    playerState = playerState,
                    onPlayPause = { viewModel.onPlayPause() },
                    onSkipNext = { viewModel.onSkipNext() },
                    onSkipPrevious = { viewModel.onSkipPrevious() },
                    onShuffle = { viewModel.onShuffle() },
                    onRepeat = { viewModel.onRepeat() },
                    onSeek = { viewModel.onSeek(it) }
                )

                QueueSection(
                    visible = showQueue,
                    queue = playerState.queue,
                    currentIdx = playerState.currentIndex,
                    favoriteIds = viewModel.favoriteIds.collectAsState().value,
                    onPlayFromIndex = { viewModel.onPlayFromIndex(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                )
            }
        } else {
            EmptyNowPlayingState()
        }
    }
}

@Composable
private fun AlbumArtSection(
    currentSong: Song,
    artworks: List<AlbumArtwork>,
    modifier: Modifier = Modifier
) {
    if (artworks.isNotEmpty()) {
        ArtworkPager(
            artworks = artworks,
            modifier = modifier.size(280.dp),
            scrollKey = currentSong.id
        )
    } else if (currentSong.albumArtUriLarge != null) {
        AsyncImage(
            model = currentSong.albumArtUriLarge,
            contentDescription = stringResource(R.string.cd_album_art),
            modifier = modifier
                .size(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Fit
        )
    } else {
        AlbumArtPlaceholder(
            modifier = modifier.size(280.dp),
            iconSize = 96.dp
        )
    }
}

@Composable
private fun SongInfoSection(
    song: Song,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MarqueeText(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = song.artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = song.albumName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QueueSection(
    visible: Boolean,
    queue: List<Song>,
    currentIdx: Int,
    favoriteIds: Set<String>,
    onPlayFromIndex: (Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && queue.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
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
                itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = index == currentIdx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayFromIndex(index) }
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
                                        text = song.artistName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { onToggleFavorite(song) }) {
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

@Composable
private fun EmptyNowPlayingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
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
