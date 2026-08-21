package com.schwanitz.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.common.filterSongs
import com.schwanitz.ui.common.toggleFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

data class HomeUiState(
    val songs: List<Song> = emptyList(),
    val totalSongCount: Int? = null,
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val showFavoritesOnly: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    val errorHolder = ErrorHolder()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _filterQuery = MutableStateFlow("")
    private var searchJob: Job? = null
    private val _showFavoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        _filterQuery,
        _showFavoritesOnly,
        songRepository.getAllSongs()
    ) { query, favoritesOnly, songs ->
        HomeUiState(
            songs = withContext(Dispatchers.Default) { songs.filterSongs(query, favoritesOnly) },
            totalSongCount = songs.size,
            isLoading = false,
            searchQuery = query,
            showFavoritesOnly = favoritesOnly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

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

    fun playSong(song: Song) {
        playerManager.play(song, listOf(song))
    }

    fun toggleFavorite(song: Song) = toggleFavorite(song, songRepository, errorHolder)
}
