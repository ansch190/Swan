package com.schwanitz.ui.screens.artistdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Artist
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.ArtistRepository
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val artistRepository: ArtistRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _artistImageUri = MutableStateFlow<String?>(null)
    val artistImageUri: StateFlow<String?> = _artistImageUri

    private val _artistBiography = MutableStateFlow<String?>(null)
    val artistBiography: StateFlow<String?> = _artistBiography

    fun loadArtistByName(artistName: String) {
        viewModelScope.launch {
            val artist = artistRepository.getArtistByName(artistName) ?: return@launch
            loadArtist(artist.id, artist.name)
        }
    }

    fun loadArtist(artistId: Long, artistName: String) {
        viewModelScope.launch {
            musicRepository.getSongsByArtistId(artistId).collect {
                _songs.value = it
            }
        }
        viewModelScope.launch {
            musicRepository.getAlbumsByArtistId(artistId).collect {
                _albums.value = it
            }
        }
        viewModelScope.launch {
            _artistImageUri.value = artistRepository.getArtistImageLarge(artistId)
        }
        viewModelScope.launch {
            _artistBiography.value = artistRepository.getArtistBiography(artistId)
        }
    }

    fun playSong(song: Song, queue: List<Song>) {
        playerManager.play(song, queue)
    }
}
