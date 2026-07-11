package com.schwanitz.ui.screens.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.R
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val FAVORITES_PLAYLIST_ID = -1L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerManager: MusicPlayerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _playlistId = MutableStateFlow<Long?>(null)

    val isFavoritesPlaylist: StateFlow<Boolean> = _playlistId
        .map { it == FAVORITES_PLAYLIST_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playlistName: StateFlow<String> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            if (id == FAVORITES_PLAYLIST_ID) {
                flowOf(context.getString(R.string.playlist_favorites_name))
            } else {
                playlistRepository.getPlaylistName(id).map { it ?: context.getString(R.string.playlist_default_name) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.getString(R.string.playlist_default_name))

    val songs: StateFlow<List<Song>> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            if (id == FAVORITES_PLAYLIST_ID) {
                musicRepository.getFavoriteSongs()
            } else {
                playlistRepository.getPlaylistSongs(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadPlaylist(playlistId: Long) {
        _playlistId.value = playlistId
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            _playlistId.value?.let { id ->
                if (id != FAVORITES_PLAYLIST_ID) {
                    playlistRepository.renamePlaylist(id, newName)
                }
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

    fun savePlaylistChanges(songIds: List<String>, deleteSongIds: List<String>) {
        viewModelScope.launch {
            val pid = _playlistId.value ?: return@launch
            if (pid == FAVORITES_PLAYLIST_ID) return@launch
            deleteSongIds.forEach { playlistRepository.removeSongFromPlaylist(pid, it) }
            playlistRepository.reorderSongs(pid, songIds)
        }
    }
}
