package com.schwanitz.ui.screens.yeardetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YearDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    fun loadYear(year: Int) {
        viewModelScope.launch {
            musicRepository.getSongsByYear(year).collect {
                _songs.value = it
            }
        }
        viewModelScope.launch {
            musicRepository.getAlbumsByYear(year).collect {
                _albums.value = it
            }
        }
    }

    fun playSong(song: Song) {
        playerManager.play(song, listOf(song))
    }

    fun playAllFromSong(song: Song) {
        playerManager.play(song, songs.value)
    }
}
