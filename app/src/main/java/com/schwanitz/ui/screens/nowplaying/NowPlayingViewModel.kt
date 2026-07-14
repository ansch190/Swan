package com.schwanitz.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.common.toggleFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val playerManager: MusicPlayerManager,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val errorHolder = ErrorHolder()

    val playerState: StateFlow<com.schwanitz.player.PlayerState> =
        playerManager.playerState
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerManager.playerState.value)

    private val _artworks = MutableStateFlow<List<AlbumArtwork>>(emptyList())
    val artworks: StateFlow<List<AlbumArtwork>> = _artworks

    val favoriteIds: StateFlow<Set<String>> = musicRepository.getFavoriteSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private var currentAlbumId: Long? = null

    fun loadArtworks(albumId: Long?) {
        if (albumId == currentAlbumId) return
        currentAlbumId = albumId
        viewModelScope.launch {
            runCatching {
                _artworks.value = if (albumId != null) {
                    musicRepository.getAlbumArtworks(albumId)
                } else {
                    emptyList()
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun toggleFavorite(song: Song) = toggleFavorite(song, musicRepository, errorHolder)
}
