package com.schwanitz.ui.screens.genredetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.ArtistRepository
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
class GenreDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _artists = MutableStateFlow<List<String>>(emptyList())
    val artists: StateFlow<List<String>> = _artists

    val errorHolder = ErrorHolder()

    private val _artistImageUris = MutableStateFlow<Map<String, String?>>(emptyMap())
    val artistImageUris: StateFlow<Map<String, String?>> = _artistImageUris
    private val imageUris = mutableMapOf<String, String?>()

    fun loadGenre(genre: String) {
        viewModelScope.launch {
            runCatching {
                launch {
                    musicRepository.getSongsByGenre(genre).collect { _songs.value = it }
                }
                launch {
                    musicRepository.getAlbumsByGenre(genre).collect { _albums.value = it }
                }
                launch {
                    musicRepository.getArtistsByGenre(genre).collect {
                        _artists.value = it
                        loadArtistImages()
                    }
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    private suspend fun loadArtistImages() {
        for (artist in _artists.value) {
            try {
                if (artist.isBlank()) {
                    imageUris[artist] = null
                    _artistImageUris.value = imageUris.toMap()
                    continue
                }
                if (!imageUris.containsKey(artist)) {
                    imageUris[artist] = null
                    _artistImageUris.value = imageUris.toMap()
                    val artistEntity = artistRepository.getArtistByName(artist)
                    val uri = artistEntity?.let { artistRepository.getArtistImageSmall(it.id) }
                    imageUris[artist] = uri
                    _artistImageUris.value = imageUris.toMap()
                }
            } catch (e: Exception) {
                // skip this artist, continue with next
            }
        }
    }

    fun playSong(song: Song) {
        playerManager.play(song, listOf(song))
    }

    fun playAllFromSong(song: Song) {
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
