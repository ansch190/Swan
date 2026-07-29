package com.schwanitz.ui.screens.yeardetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.common.ArtistImageLoader
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.components.SelectionDelegate
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YearDetailViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playerManager: MusicPlayerManager,
    private val artistImageLoader: ArtistImageLoader,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val errorHolder = ErrorHolder()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _artists = MutableStateFlow<List<String>>(emptyList())
    val artists: StateFlow<List<String>> = _artists

    val artistImageUris: StateFlow<Map<String, String?>> = artistImageLoader.artistImageUris

    fun loadYear(year: Int) {
        viewModelScope.launch {
            runCatching {
                launch {
                    songRepository.getSongsByYear(year).collect { _songs.value = it }
                }
                launch {
                    songRepository.getAlbumsByYear(year).collect { _albums.value = it }
                }
                launch {
                    songRepository.getAlbumArtistsByYear(year).collect {
                        _artists.value = it
                        artistImageLoader.loadForArtists(it)
                    }
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun playSong(song: Song) {
        playerManager.play(song, listOf(song))
    }

    fun playAllFromSong(song: Song) {
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
