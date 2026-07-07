package com.schwanitz.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistListItemData(
    val id: Long,
    val name: String,
    val songCount: Int
)

data class PlaylistListUiState(
    val playlists: List<PlaylistListItemData> = emptyList()
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val uiState: StateFlow<PlaylistListUiState> =
        combine(
            playlistRepository.getAllPlaylists(),
            playlistRepository.getAllPlaylistSongCounts()
        ) { playlists, counts ->
            PlaylistListUiState(
                playlists = playlists.map { p ->
                    PlaylistListItemData(
                        id = p.id,
                        name = p.name,
                        songCount = counts[p.id] ?: 0
                    )
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistListUiState())

    fun showCreateDialog() {
        viewModelScope.launch {
            // Will be implemented with dialog state
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }
}
