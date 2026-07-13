package com.schwanitz.ui.components

import com.schwanitz.domain.model.Playlist
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.common.ErrorHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SelectionDelegate(
    private val playerManager: MusicPlayerManager,
    private val playlistRepository: PlaylistRepository,
    private val scope: CoroutineScope,
    private val songsProvider: () -> List<Song>,
    private val errorHolder: ErrorHolder = ErrorHolder()
) {
    private val _isSelecting = MutableStateFlow(false)
    val isSelecting: StateFlow<Boolean> = _isSelecting

    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enterSelection(song: Song) {
        _isSelecting.value = true
        _selectedSongIds.value = setOf(song.id)
    }

    fun exitSelection() {
        _isSelecting.value = false
        _selectedSongIds.value = emptySet()
    }

    fun toggleSelection(songId: String) {
        _selectedSongIds.value = _selectedSongIds.value.let {
            if (songId in it) it - songId else it + songId
        }
        if (_selectedSongIds.value.isEmpty()) _isSelecting.value = false
    }

    fun playSelection() {
        val ids = _selectedSongIds.value
        val selected = songsProvider().filter { it.id in ids }
        if (selected.isNotEmpty()) {
            playerManager.play(selected.first(), selected)
        }
        exitSelection()
    }

    fun addSelectionToQueue() {
        val ids = _selectedSongIds.value
        val selected = songsProvider().filter { it.id in ids }
        if (selected.isNotEmpty()) {
            playerManager.addToQueue(selected)
        }
        exitSelection()
    }

    fun addSelectionToPlaylist(playlistId: Long) {
        val ids = _selectedSongIds.value
        val selected = songsProvider().filter { it.id in ids }
        scope.launch {
            runCatching {
                val count = playlistRepository.getPlaylistSongCount(playlistId)
                selected.forEachIndexed { index, song ->
                    playlistRepository.addSongToPlaylist(playlistId, song.id, count + index)
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
            exitSelection()
        }
    }
}
