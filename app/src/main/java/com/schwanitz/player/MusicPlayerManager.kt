package com.schwanitz.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.schwanitz.data.source.AuthHttpDataSourceFactory
import com.schwanitz.domain.error.AppError
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

data class PlayerState(
    val currentSong: Song? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val error: AppError? = null
)

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val player: ExoPlayer,
    private val authHttpDataSourceFactory: AuthHttpDataSourceFactory,
    private val sourceManager: SourceManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var songQueue: List<Song> = emptyList()

    init {
        scope.launch {
            sourceManager.sources.collect { sources ->
                authHttpDataSourceFactory.updateSources(sources)
            }
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _playerState.value = _playerState.value.copy(
                        duration = player.duration.coerceAtLeast(0)
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index in songQueue.indices) {
                    _playerState.value = _playerState.value.copy(
                        currentSong = songQueue[index],
                        currentIndex = index
                    )
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Timber.e(error, "Playback error: %s", error.message)
                _playerState.update { it.copy(
                    error = AppError.playback(cause = error, message = error.message ?: "Playback error")
                ) }
            }
        })
    }

    fun play(song: Song, queue: List<Song> = listOf(song)) {
        Timber.i("Playing: '%s' by %s (queue: %d songs)", song.title, song.artistName, queue.size)
        try {
            appContext.startService(
                Intent(appContext, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start service")
        }
        songQueue = queue
        player.stop()
        player.clearMediaItems()

        queue.forEach { s -> player.addMediaItem(buildMediaItem(s)) }

        val playIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.seekTo(playIndex, 0)

        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF

        player.prepare()
        player.play()

        _playerState.value = _playerState.value.copy(
            currentSong = song,
            currentIndex = playIndex,
            queue = queue,
            shuffleMode = false,
            repeatMode = Player.REPEAT_MODE_OFF
        )
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        Timber.d("Adding %d songs to queue", songs.size)
        try {
            appContext.startService(
                Intent(appContext, MusicPlayerService::class.java).apply {
                    action = MusicPlayerService.ACTION_PLAY
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start service")
        }
        val needsPrepare = player.playbackState == Player.STATE_IDLE
        songs.forEach { s -> player.addMediaItem(buildMediaItem(s)) }
        songQueue = songQueue + songs
        if (needsPrepare) {
            player.prepare()
            player.play()
        }
        _playerState.value = _playerState.value.copy(queue = songQueue)
    }

    private fun buildMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.filePath)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistName)
                    .setAlbumTitle(song.albumName)
                    .setArtworkUri((song.albumArtUriLarge ?: song.albumArtUri)?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    fun togglePlayPause() {
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0, 0)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun playFromIndex(index: Int) {
        if (index in songQueue.indices) {
            player.seekTo(index, 0)
            player.play()
        }
    }

    fun skipToNext() {
        player.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun toggleShuffle() {
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        player.repeatMode = if (player.shuffleModeEnabled) {
            Player.REPEAT_MODE_ALL
        } else {
            Player.REPEAT_MODE_OFF
        }
        _playerState.value = _playerState.value.copy(
            shuffleMode = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    fun cycleRepeatMode() {
        player.repeatMode = when {
            player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ALL -> {
                player.shuffleModeEnabled = false
                Player.REPEAT_MODE_OFF
            }
            player.repeatMode == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            player.repeatMode == Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _playerState.value = _playerState.value.copy(
            shuffleMode = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionJob = scope.launch {
            while (isActive) {
                _playerState.value = _playerState.value.copy(
                    currentPosition = player.currentPosition.coerceAtLeast(0)
                )
                delay(100.milliseconds)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }
}
