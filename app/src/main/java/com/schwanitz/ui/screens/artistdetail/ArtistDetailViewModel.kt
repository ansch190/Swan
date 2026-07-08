package com.schwanitz.ui.screens.artistdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.ArtistProfile
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.ArtistImageRepository
import com.schwanitz.domain.repository.ArtistProfileRepository
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
    private val artistImageRepository: ArtistImageRepository,
    private val artistProfileRepository: ArtistProfileRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _artistImageUri = MutableStateFlow<String?>(null)
    val artistImageUri: StateFlow<String?> = _artistImageUri

    private val _artistProfile = MutableStateFlow<ArtistProfile?>(null)
    val artistProfile: StateFlow<ArtistProfile?> = _artistProfile

    fun loadArtist(artistName: String) {
        viewModelScope.launch {
            musicRepository.getSongsByArtist(artistName).collect {
                _songs.value = it
            }
        }
        viewModelScope.launch {
            musicRepository.getAlbumsByArtist(artistName).collect {
                _albums.value = it
            }
        }
        viewModelScope.launch {
            _artistImageUri.value = artistImageRepository.getArtistImage(artistName)
        }
        viewModelScope.launch {
            _artistProfile.value = artistProfileRepository.getArtistProfile(artistName)
        }
    }

    fun playSong(song: Song, queue: List<Song>) {
        playerManager.play(song, queue)
    }
}
