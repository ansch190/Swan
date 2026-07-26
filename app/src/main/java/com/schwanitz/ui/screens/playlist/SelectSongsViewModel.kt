package com.schwanitz.ui.screens.playlist

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.R
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.schwanitz.ui.common.ErrorHolder
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
    private val playlistRepository: PlaylistRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val errorHolder = ErrorHolder()

    val playlistId: Long = savedStateHandle.get<Long>("playlistId") ?: 0L

    private val _searchQuery = MutableStateFlow("")
    private val _showFavoritesOnly = MutableStateFlow(false)

    val selectedSongIds = MutableStateFlow<Set<String>>(emptySet())

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

    fun confirmSelection(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val songIds = selectedSongIds.value.toList()
                var duplicateCount = 0
                val count = playlistRepository.getPlaylistSongCount(playlistId)
                songIds.forEachIndexed { index, songId ->
                    val added = playlistRepository.addSongToPlaylist(playlistId, songId, count + index)
                    if (!added) duplicateCount++
                }
                if (duplicateCount > 0) {
                    errorHolder.emit(
                        com.schwanitz.domain.error.AppError.Unknown(
                            message = context.getString(R.string.playlist_songs_already_exist, duplicateCount)
                        )
                    )
                }
                onComplete()
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }
}
