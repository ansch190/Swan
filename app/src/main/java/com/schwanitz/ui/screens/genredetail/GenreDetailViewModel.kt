package com.schwanitz.ui.screens.genredetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.ArtistImageRepository
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val artistImageRepository: ArtistImageRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _artists = MutableStateFlow<List<String>>(emptyList())
    val artists: StateFlow<List<String>> = _artists

    private val _artistImageUris = MutableStateFlow<Map<String, String?>>(emptyMap())
    val artistImageUris: StateFlow<Map<String, String?>> = _artistImageUris

    fun loadGenre(genre: String) {
        viewModelScope.launch {
            musicRepository.getSongsByGenre(genre).collect {
                _songs.value = it
            }
        }
        viewModelScope.launch {
            musicRepository.getAlbumsByGenre(genre).collect {
                _albums.value = it
            }
        }
        viewModelScope.launch {
            musicRepository.getArtistsByGenre(genre).collect {
                _artists.value = it
                loadArtistImages(it)
            }
        }
    }

    private fun loadArtistImages(artists: List<String>) {
        artists.forEach { artist ->
            if (artist !in _artistImageUris.value) {
                _artistImageUris.value = _artistImageUris.value + (artist to null)
                viewModelScope.launch {
                    val uri = artistImageRepository.getArtistImage(artist)
                    _artistImageUris.value = _artistImageUris.value + (artist to uri)
                }
            }
        }
    }

    fun playSong(song: Song, queue: List<Song>) {
        playerManager.play(song, queue)
    }
}
