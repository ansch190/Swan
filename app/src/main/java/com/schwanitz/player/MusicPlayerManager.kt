package com.schwanitz.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.schwanitz.data.source.AuthHttpDataSourceFactory
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerState(
    val currentSong: Song? = null,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val error: String? = null
)

@Singleton
class MusicPlayerManager @Inject constructor(
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
                _playerState.value = _playerState.value.copy(error = error.message)
            }
        })
    }

    fun play(song: Song, queue: List<Song> = listOf(song)) {
        songQueue = queue
        player.stop()
        player.clearMediaItems()

        queue.forEachIndexed { index, s ->
            val builder = MediaItem.Builder()
                .setMediaId(s.id)
                .setUri(s.filePath)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .build()
                )
            player.addMediaItem(builder.build())
        }

        val playIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.seekTo(playIndex, 0)
        player.prepare()
        player.play()

        _playerState.value = _playerState.value.copy(
            currentSong = song,
            currentIndex = playIndex,
            queue = queue,
            error = null
        )
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
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        _playerState.value = _playerState.value.copy(
            shuffleMode = player.shuffleModeEnabled
        )
    }

    fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        _playerState.value = _playerState.value.copy(
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
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    fun release() {
        scope.cancel()
        player.release()
    }
}
