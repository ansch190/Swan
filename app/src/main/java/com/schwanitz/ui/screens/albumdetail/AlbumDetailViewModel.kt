package com.schwanitz.ui.screens.albumdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.local.dao.AlbumDao
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val albumDao: AlbumDao,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _artworks = MutableStateFlow<List<AlbumArtwork>>(emptyList())
    val artworks: StateFlow<List<AlbumArtwork>> = _artworks

    private val _series = MutableStateFlow<AlbumSeries?>(null)
    val series: StateFlow<AlbumSeries?> = _series

    fun loadAlbum(albumName: String, albumArtistName: String) {
        viewModelScope.launch {
            val album = albumDao.findByNameAndAlbumArtist(albumName, albumArtistName) ?: return@launch
            launch {
                musicRepository.getSongsByAlbumId(album.id).collect { albumSongs ->
                    android.util.Log.d("AlbumDetailVM", "Loaded ${albumSongs.size} songs for album: $albumName")
                    _songs.value = albumSongs
                    if (albumSongs.isNotEmpty()) {
                        val albumId = albumSongs.first().albumId
                        _artworks.value = if (albumId != null) {
                            musicRepository.getAlbumArtworks(albumId)
                        } else {
                            emptyList()
                        }
                    }
                }
            }
            launch {
                musicRepository.getSeriesForAlbum(album.id).collect { s ->
                    _series.value = s
                }
            }
        }
    }

    fun playSong(song: Song) {
        playerManager.play(song, listOf(song))
    }

    fun playAllFromSong(song: Song, cdSongs: List<Song>) {
        playerManager.play(song, cdSongs)
    }

    fun playEntireAlbum(song: Song) {
        playerManager.play(song, songs.value)
    }

    private val _isSelecting = MutableStateFlow(false)
    val isSelecting: StateFlow<Boolean> = _isSelecting

    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds

    val playlists: StateFlow<List<com.schwanitz.domain.model.Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        val selected = songs.value.filter { it.id in ids }
        if (selected.isNotEmpty()) {
            playerManager.play(selected.first(), selected)
        }
        exitSelection()
    }

    fun addSelectionToQueue() {
        val ids = _selectedSongIds.value
        val selected = songs.value.filter { it.id in ids }
        if (selected.isNotEmpty()) {
            playerManager.addToQueue(selected)
        }
        exitSelection()
    }

    fun addSelectionToPlaylist(playlistId: Long) {
        val ids = _selectedSongIds.value
        val selected = songs.value.filter { it.id in ids }
        viewModelScope.launch {
            val count = playlistRepository.getPlaylistSongCount(playlistId)
            selected.forEachIndexed { index, song ->
                playlistRepository.addSongToPlaylist(playlistId, song.id, count + index)
            }
            exitSelection()
        }
    }
}
