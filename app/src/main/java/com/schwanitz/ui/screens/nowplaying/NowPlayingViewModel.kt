package com.schwanitz.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.genius.GeniusLyricsProvider
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.AlbumRepository
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.common.toggleFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerManager: MusicPlayerManager,
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val lyricsProvider: GeniusLyricsProvider
) : ViewModel() {

    val errorHolder = ErrorHolder()

    val playerState: StateFlow<com.schwanitz.player.PlayerState> =
        playerManager.playerState
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerManager.playerState.value)

    private val _artworks = MutableStateFlow<List<AlbumArtwork>>(emptyList())
    val artworks: StateFlow<List<AlbumArtwork>> = _artworks

    val favoriteIds: StateFlow<Set<String>> = songRepository.getFavoriteSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics

    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading

    private var lyricsLoadJob: Job? = null

    fun loadLyrics(songId: String, title: String, artist: String) {
        lyricsLoadJob?.cancel()
        lyricsLoadJob = viewModelScope.launch {
            _lyricsLoading.value = true
            _lyrics.value = null
            runCatching {
                lyricsProvider.getLyrics(songId, title, artist)
            }.onSuccess { result ->
                _lyrics.value = result
            }.onFailure {
                _lyrics.value = null
            }
            _lyricsLoading.value = false
        }
    }

    fun clearLyrics() {
        _lyrics.value = null
        _lyricsLoading.value = false
    }

    private var currentAlbumId: Long? = null

    fun loadArtworks(albumId: Long?) {
        if (albumId == currentAlbumId) return
        currentAlbumId = albumId
        viewModelScope.launch {
            runCatching {
                _artworks.value = if (albumId != null) {
                    albumRepository.getAlbumArtworks(albumId)
                } else {
                    emptyList()
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun toggleFavorite(song: Song) = toggleFavorite(song, songRepository, errorHolder)

    fun onPlayPause() = playerManager.togglePlayPause()
    fun onSkipNext() = playerManager.skipToNext()
    fun onSkipPrevious() = playerManager.skipToPrevious()
    fun onShuffle() = playerManager.toggleShuffle()
    fun onRepeat() = playerManager.cycleRepeatMode()
    fun onSeek(positionMs: Long) = playerManager.seekTo(positionMs)
    fun onPlayFromIndex(index: Int) = playerManager.playFromIndex(index)
}
