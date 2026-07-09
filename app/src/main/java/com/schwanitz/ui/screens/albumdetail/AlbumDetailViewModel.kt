package com.schwanitz.ui.screens.albumdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _artworks = MutableStateFlow<List<SongArtwork>>(emptyList())
    val artworks: StateFlow<List<SongArtwork>> = _artworks

    private val _series = MutableStateFlow<AlbumSeries?>(null)
    val series: StateFlow<AlbumSeries?> = _series

    fun loadAlbum(albumName: String, artistName: String) {
        viewModelScope.launch {
            musicRepository.getSongsByAlbum(albumName).collect { albumSongs ->
                android.util.Log.d("AlbumDetailVM", "Loaded ${albumSongs.size} songs for album: $albumName")
                _songs.value = albumSongs
                if (albumSongs.isNotEmpty()) {
                    _artworks.value = musicRepository.getSongArtworks(albumSongs.first().id)
                }
            }
        }
        viewModelScope.launch {
            musicRepository.getSeriesForAlbum(albumName).collect { s ->
                _series.value = s
            }
        }
    }

    fun playSong(song: Song, cdSongs: List<Song>) {
        playerManager.play(song, cdSongs)
    }
}
