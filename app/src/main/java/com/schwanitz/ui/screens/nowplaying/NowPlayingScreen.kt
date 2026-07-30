package com.schwanitz.ui.screens.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    onSongInfoClick: (String) -> Unit = {},
    onAddToPlaylist: (String) -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    LaunchedEffect(showQueue) { if (!showQueue) isEditing = false }
    var selectedSongIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    val lyrics by viewModel.lyrics.collectAsState()
    val lyricsLoading by viewModel.lyricsLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nowplaying_title)) },
            actions = {
                val currentSong = playerState.currentSong
                if (currentSong != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                onClick = { onSongInfoClick(currentSong.id) },
                                onLongClick = {
                                    viewModel.loadLyrics(
                                        currentSong.id,
                                        currentSong.title,
                                        currentSong.artistName
                                    )
                                    showLyricsDialog = true
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.songinfo_title)
                        )
                    }
                    IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = if (showQueue) Icons.Filled.Image else Icons.AutoMirrored.Filled.List,
                        contentDescription = if (showQueue) stringResource(R.string.cd_show_album_art) else stringResource(R.string.cd_show_queue)
                    )
                }
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
                    isEditing = isEditing,
                    selectedSongIds = selectedSongIds,
                    onPlayFromIndex = { viewModel.onPlayFromIndex(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onMoveInQueue = { from, to -> viewModel.moveInQueue(from, to) },
                    onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                    onToggleEdit = { isEditing = !isEditing },
                    onToggleSelection = { songId ->
                        selectedSongIds = selectedSongIds.let {
                            if (songId in it) it - songId else it + songId
                        }
                    },
                    onAddSelectedToPlaylist = {
                        onAddToPlaylist(selectedSongIds.joinToString(","))
                        selectedSongIds = emptySet()
                    }
                )
            }
        } else {
            EmptyNowPlayingState()
        }

        if (showLyricsDialog) {
            AlertDialog(
                onDismissRequest = {
                    showLyricsDialog = false
                    viewModel.clearLyrics()
                },
                title = { Text(stringResource(R.string.songinfo_lyrics_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when {
                            lyricsLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            lyrics != null -> {
                                Text(text = lyrics!!)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.source_format, "Genius"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                Text(
                                    text = stringResource(R.string.songinfo_lyrics_not_found),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLyricsDialog = false
                        viewModel.clearLyrics()
                    }) {
                        Text(stringResource(R.string.songinfo_lyrics_dismiss))
                    }
                }
            )
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

private data class QueueEditItem(val stableId: Long, val song: Song)

@Composable
private fun QueueSection(
    visible: Boolean,
    queue: List<Song>,
    currentIdx: Int,
    favoriteIds: Set<String>,
    isEditing: Boolean,
    selectedSongIds: Set<String>,
    onPlayFromIndex: (Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onToggleEdit: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onAddSelectedToPlaylist: () -> Unit,
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

            var editItems by remember { mutableStateOf(listOf<QueueEditItem>()) }
            var nextEditId by remember { mutableStateOf(0L) }

            LaunchedEffect(isEditing) {
                if (isEditing) {
                    editItems = queue.map { QueueEditItem(nextEditId++, it) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nowplaying_queue_header),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedSongIds.isEmpty()) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            imageVector = if (isEditing) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val lazyListState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                editItems = editItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                onMoveInQueue(from.index, to.index)
            }

            LaunchedEffect(currentIdx, isEditing) {
                if (currentIdx >= 0 && !isEditing) lazyListState.animateScrollToItem(currentIdx)
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.heightIn(max = 340.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                if (isEditing) {
                    itemsIndexed(editItems, key = { _, item -> item.stableId }) { index, item ->
                        ReorderableItem(reorderableState, key = item.stableId) { isDragging ->
                            val alpha = if (isDragging) 0.7f else 1f
                            QueueRow(
                                song = item.song,
                                isCurrent = index == currentIdx,
                                isEditing = true,
                                isSelecting = false,
                                isSelected = false,
                                favoriteIds = favoriteIds,
                                alpha = alpha,
                                dragHandleModifier = Modifier.draggableHandle(),
                                onToggleFavorite = { onToggleFavorite(item.song) },
                                onRemoveFromQueue = {
                                    editItems = editItems.toMutableList().apply { removeAt(index) }
                                    onRemoveFromQueue(index)
                                },
                                onPlayClick = {},
                                onLongClick = {},
                                onToggleSelection = {},
                                onAddToPlaylist = {},
                                modifier = Modifier.alpha(alpha)
                            )
                        }
                    }
                } else {
                    itemsIndexed(queue, key = { index, song -> "$index:${song.id}" }) { index, song ->
                        QueueRow(
                            song = song,
                            isCurrent = index == currentIdx,
                            isEditing = false,
                            isSelecting = selectedSongIds.isNotEmpty(),
                            isSelected = song.id in selectedSongIds,
                            favoriteIds = favoriteIds,
                            alpha = 1f,
                            dragHandleModifier = Modifier,
                            onToggleFavorite = { onToggleFavorite(song) },
                            onRemoveFromQueue = {},
                            onPlayClick = { onPlayFromIndex(index) },
                            onLongClick = { onToggleSelection(song.id) },
                            onToggleSelection = { onToggleSelection(song.id) },
                            onAddToPlaylist = onAddSelectedToPlaylist,
                            modifier = Modifier
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    song: Song,
    isCurrent: Boolean,
    isEditing: Boolean,
    isSelecting: Boolean,
    isSelected: Boolean,
    favoriteIds: Set<String>,
    alpha: Float,
    dragHandleModifier: Modifier,
    onToggleFavorite: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onPlayClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    when {
                        isEditing -> Modifier
                        isSelecting -> Modifier.combinedClickable(
                            onClick = { onToggleSelection() },
                            onLongClick = {
                                if (isSelected) showMenu = true
                                else onToggleSelection()
                            }
                        )
                        else -> Modifier.combinedClickable(
                            onClick = onPlayClick,
                            onLongClick = onLongClick
                        )
                    }
                )
                .padding(vertical = 6.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = when {
                    isSelecting -> if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                   else MaterialTheme.colorScheme.surface
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isSelecting && isSelected -> Icons.Filled.Check
                            isCurrent -> Icons.Filled.PlayArrow
                            else -> Icons.Filled.MusicNote
                        },
                        contentDescription = null,
                        tint = when {
                            isSelecting && isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                            isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        MarqueeText(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isSelecting && isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                                isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = song.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isSelecting && isSelected -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isEditing) {
                        IconButton(
                            onClick = {},
                            modifier = dragHandleModifier
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onRemoveFromQueue) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (song.id in favoriteIds) Icons.Filled.Favorite
                                              else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                                tint = if (song.id in favoriteIds) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_add_to_playlist)) },
                onClick = {
                    showMenu = false
                    onAddToPlaylist()
                }
            )
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
