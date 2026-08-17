package com.schwanitz.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistPickerItem(
    val id: Long,
    val name: String,
    val songCount: Int
)

data class PlaylistPickerUiState(
    val playlists: List<PlaylistPickerItem> = emptyList()
)

data class PlaylistAddOutcome(
    val addedCount: Int,
    val duplicateCount: Int
)

@HiltViewModel
class PlaylistPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val errorHolder = ErrorHolder()

    private val songIds: List<String> = run {
        val raw = savedStateHandle.get<String>("songIds") ?: ""
        if (raw.isBlank()) emptyList() else raw.split(",")
    }

    val uiState: StateFlow<PlaylistPickerUiState> =
        combine(
            playlistRepository.getAllPlaylists(),
            playlistRepository.getAllPlaylistSongCounts()
        ) { playlists, counts ->
            PlaylistPickerUiState(
                playlists = playlists.map { p ->
                    PlaylistPickerItem(
                        id = p.id,
                        name = p.name,
                        songCount = counts[p.id] ?: 0
                    )
                }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistPickerUiState())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId

    fun addSongsToPlaylist(playlistId: Long, onComplete: (PlaylistAddOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = runCatching {
                var duplicateCount = 0
                val count = playlistRepository.getPlaylistSongCount(playlistId)
                songIds.forEachIndexed { index, songId ->
                    val added = playlistRepository.addSongToPlaylist(playlistId, songId, count + index)
                    if (!added) duplicateCount++
                }
                PlaylistAddOutcome(
                    addedCount = songIds.size - duplicateCount,
                    duplicateCount = duplicateCount
                )
            }.getOrElse {
                errorHolder.emit(it)
                return@launch
            }
            onComplete(outcome)
        }
    }

    fun createPlaylistAndAddSongs(name: String, onComplete: (PlaylistAddOutcome) -> Unit) {
        viewModelScope.launch {
            val newId = runCatching {
                playlistRepository.createPlaylist(name)
            }.getOrElse {
                errorHolder.emit(it)
                -1L
            }
            if (newId > 0) {
                addSongsToPlaylist(newId, onComplete)
            }
        }
    }
}
