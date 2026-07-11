package com.schwanitz.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectSongsUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false
)

@HiltViewModel
class SelectSongsViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L

    private val _searchQuery = MutableStateFlow("")
    private val _showFavoritesOnly = MutableStateFlow(false)

    val selectedSongIds = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SelectSongsUiState> = combine(
        _searchQuery,
        _showFavoritesOnly,
        musicRepository.getAllSongs()
    ) { query, favoritesOnly, songs ->
        val filtered = when {
            query.isNotBlank() -> songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true) ||
                    it.albumName.contains(query, ignoreCase = true)
            }
            favoritesOnly -> songs.filter { it.isFavorite }
            else -> songs
        }
        SelectSongsUiState(
            songs = filtered,
            isLoading = false,
            searchQuery = query,
            showFavoritesOnly = favoritesOnly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SelectSongsUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    fun toggleSongSelection(songId: String) {
        selectedSongIds.value = selectedSongIds.value.let { current ->
            if (songId in current) current - songId else current + songId
        }
    }

    fun confirmSelection(onComplete: () -> Unit) {
        viewModelScope.launch {
            val songIds = selectedSongIds.value.toList()
            val count = playlistRepository.getPlaylistSongCount(playlistId)
            songIds.forEachIndexed { index, songId ->
                playlistRepository.addSongToPlaylist(playlistId, songId, count + index)
            }
            onComplete()
        }
    }
}
