package com.schwanitz.ui.screens.albumdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.AlbumRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.domain.repository.SeriesRepository
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.components.SelectionDelegate
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val seriesRepository: SeriesRepository,
    private val playerManager: MusicPlayerManager,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _artworks = MutableStateFlow<List<AlbumArtwork>>(emptyList())
    val artworks: StateFlow<List<AlbumArtwork>> = _artworks

    private val _series = MutableStateFlow<AlbumSeries?>(null)
    val series: StateFlow<AlbumSeries?> = _series

    private val _year = MutableStateFlow(0)
    val year: StateFlow<Int> = _year

    val errorHolder = ErrorHolder()

    fun loadAlbum(albumName: String, albumArtistName: String) {
        viewModelScope.launch {
            runCatching {
                val album = albumRepository.findAlbumByNameAndArtist(albumName, albumArtistName) ?: return@launch
                _year.value = album.year
                launch {
                    runCatching {
                        songRepository.getSongsByAlbumId(album.id).collect { albumSongs ->
                            _songs.value = albumSongs
                            if (albumSongs.isNotEmpty()) {
                                val albumId = albumSongs.first().albumId
                                Timber.d("AlbumDetail: albumId=%s for album '%s', songs=%d", albumId, albumName, albumSongs.size)
                                _artworks.value = if (albumId != null) {
                                    val arts = albumRepository.getAlbumArtworks(albumId)
                                    Timber.d("AlbumDetail: %d artworks for albumId=%s", arts.size, albumId)
                                    arts
                                } else {
                                    Timber.w("AlbumDetail: albumId is null for album '%s'", albumName)
                                    emptyList()
                                }
                            }
                        }
                    }.exceptionOrNull()?.let { errorHolder.emit(it) }
                }
                launch {
                    runCatching {
                        seriesRepository.getSeriesForAlbum(album.id).collect { s ->
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

    private val selection = SelectionDelegate(playerManager, playlistRepository, viewModelScope, { songs.value }, errorHolder, context)
    val isSelecting: StateFlow<Boolean> = selection.isSelecting
    val selectedSongIds: StateFlow<Set<String>> = selection.selectedSongIds
    fun enterSelection(song: Song) = selection.enterSelection(song)
    fun toggleSelection(songId: String) = selection.toggleSelection(songId)
    fun playSelection() = selection.playSelection()
    fun addSelectionToQueue() = selection.addSelectionToQueue()
    fun getSelectedSongIds(): String = selection.getSelectedSongIds()
}
