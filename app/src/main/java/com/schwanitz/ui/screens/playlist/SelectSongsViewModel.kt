package com.schwanitz.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import com.schwanitz.ui.common.filterSongs
import javax.inject.Inject

data class SelectSongsUiState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false
)

@HiltViewModel
class SelectSongsViewModel @Inject constructor(
    private val songRepository: SongRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 0L

    private val _searchQuery = MutableStateFlow("")
    private val _showFavoritesOnly = MutableStateFlow(false)

    val selectedSongIds = MutableStateFlow<List<String>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SelectSongsUiState> = combine(
        _searchQuery,
        _showFavoritesOnly,
        songRepository.getAllSongs()
    ) { query, favoritesOnly, songs ->
        val filtered = songs.filterSongs(query, favoritesOnly)
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

    fun confirmSelection(onSongsSelected: (List<Song>) -> Unit) {
        val songMap = uiState.value.songs.associateBy { it.id }
        val selected = selectedSongIds.value.mapNotNull { songMap[it] }
        onSongsSelected(selected)
    }
}
