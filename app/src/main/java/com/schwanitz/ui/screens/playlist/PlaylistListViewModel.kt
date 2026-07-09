package com.schwanitz.ui.screens.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.R
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistListItemData(
    val id: Long,
    val name: String,
    val songCount: Int,
    val isFavorite: Boolean = false
)

data class PlaylistListUiState(
    val playlists: List<PlaylistListItemData> = emptyList()
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val favoritesCount = musicRepository.getFavoriteSongs()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<PlaylistListUiState> =
        combine(
            playlistRepository.getAllPlaylists(),
            playlistRepository.getAllPlaylistSongCounts(),
            favoritesCount
        ) { playlists, counts, favCount ->
            val items = playlists.map { p ->
                PlaylistListItemData(
                    id = p.id,
                    name = p.name,
                    songCount = counts[p.id] ?: 0
                )
            }
            val favoritesItem = PlaylistListItemData(
                id = -1L,
                name = context.getString(R.string.playlist_favorites_name),
                songCount = favCount,
                isFavorite = true
            )
            PlaylistListUiState(
                playlists = listOf(favoritesItem) + items
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistListUiState())

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
