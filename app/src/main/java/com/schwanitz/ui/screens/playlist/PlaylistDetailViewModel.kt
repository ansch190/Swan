package com.schwanitz.ui.screens.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _playlistId = MutableStateFlow<Long?>(null)

    val playlistName: StateFlow<String> = _playlistId
        .filterNotNull()
        .flatMapLatest { playlistRepository.getPlaylist(it) }
        .map { it?.name ?: "Playlist" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Playlist")

    val songs: StateFlow<List<Song>> = _playlistId
        .filterNotNull()
        .flatMapLatest { playlistRepository.getPlaylistSongs(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadPlaylist(playlistId: Long) {
        _playlistId.value = playlistId
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            _playlistId.value?.let { id ->
                playlistRepository.renamePlaylist(id, newName)
            }
        }
    }

    fun playSong(song: Song) {
        playerManager.play(song, songs.value)
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(song.id)
        }
    }

    fun moveSong(songIds: List<String>) {
        viewModelScope.launch {
            val pid = _playlistId.value ?: return@launch
            playlistRepository.reorderSongs(pid, songIds)
        }
    }

    fun savePlaylistChanges(songIds: List<String>, deleteSongIds: List<String>) {
        viewModelScope.launch {
            val pid = _playlistId.value ?: return@launch
            deleteSongIds.forEach { playlistRepository.removeSongFromPlaylist(pid, it) }
            playlistRepository.reorderSongs(pid, songIds)
        }
    }
}
