package com.schwanitz.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.SongArtwork
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val playerManager: MusicPlayerManager,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val playerState: StateFlow<com.schwanitz.player.PlayerState> =
        playerManager.playerState
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerManager.playerState.value)

    private val _artworks = MutableStateFlow<List<SongArtwork>>(emptyList())
    val artworks: StateFlow<List<SongArtwork>> = _artworks

    private var currentSongId: String? = null

    fun loadArtworks(songId: String) {
        if (songId == currentSongId) return
        currentSongId = songId
        viewModelScope.launch {
            _artworks.value = musicRepository.getSongArtworks(songId)
        }
    }
}
