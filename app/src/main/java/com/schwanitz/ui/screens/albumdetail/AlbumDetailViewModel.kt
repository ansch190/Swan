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
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.components.SelectionDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    val errorHolder = ErrorHolder()

    fun loadAlbum(albumName: String, albumArtistName: String) {
        viewModelScope.launch {
            runCatching {
                val album = albumDao.findByNameAndAlbumArtist(albumName, albumArtistName) ?: return@launch
                launch {
                    runCatching {
                        musicRepository.getSongsByAlbumId(album.id).collect { albumSongs ->
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
                    }.exceptionOrNull()?.let { errorHolder.emit(it) }
                }
                launch {
                    runCatching {
                        musicRepository.getSeriesForAlbum(album.id).collect { s ->
                            _series.value = s
                        }
                    }.exceptionOrNull()?.let { errorHolder.emit(it) }
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
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

    private val selection = SelectionDelegate(playerManager, playlistRepository, viewModelScope, { songs.value }, errorHolder)
    val isSelecting: StateFlow<Boolean> = selection.isSelecting
    val selectedSongIds: StateFlow<Set<String>> = selection.selectedSongIds
    val playlists: StateFlow<List<com.schwanitz.domain.model.Playlist>> = selection.playlists
    fun enterSelection(song: Song) = selection.enterSelection(song)
    fun toggleSelection(songId: String) = selection.toggleSelection(songId)
    fun playSelection() = selection.playSelection()
    fun addSelectionToQueue() = selection.addSelectionToQueue()
    fun addSelectionToPlaylist(playlistId: Long) = selection.addSelectionToPlaylist(playlistId)
}
