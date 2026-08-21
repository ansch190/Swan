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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _filterQuery = MutableStateFlow("")
    private var searchJob: Job? = null
    private val _showFavoritesOnly = MutableStateFlow(false)

    val selectedSongIds = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<SelectSongsUiState> = combine(
        _filterQuery,
        _showFavoritesOnly,
        songRepository.getAllSongs()
    ) { query, favoritesOnly, songs ->
        SelectSongsUiState(
            songs = withContext(Dispatchers.Default) { songs.filterSongs(query, favoritesOnly) },
            isLoading = false,
            searchQuery = query,
            showFavoritesOnly = favoritesOnly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SelectSongsUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isEmpty()) {
            _filterQuery.value = query
        } else {
            searchJob = viewModelScope.launch {
                delay(200)
                _filterQuery.value = query
            }
        }
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
