package com.schwanitz.ui.screens.artistdetail

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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playerManager: MusicPlayerManager,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _artistImageUri = MutableStateFlow<String?>(null)
    val artistImageUri: StateFlow<String?> = _artistImageUri

    private val _artistBiography = MutableStateFlow<String?>(null)
    val artistBiography: StateFlow<String?> = _artistBiography

    val errorHolder = ErrorHolder()

    fun loadArtistByName(artistName: String) {
        viewModelScope.launch {
            runCatching {
                if (artistName.isBlank()) {
                    Timber.d("Loading songs with no artist")
                    launch {
                        musicRepository.getSongsWithNoArtist().collect {
                            _songs.value = it
                        }
                    }
                    launch {
                        musicRepository.getAlbumsWithNoArtist().collect {
                            _albums.value = it
                        }
                    }
                } else {
                    val artist = artistRepository.getArtistByName(artistName) ?: run {
                        Timber.w("Artist not found: '%s'", artistName)
                        return@launch
                    }
                    Timber.d("Loading artist: '%s' (id=%d)", artist.name, artist.id)
                    loadArtist(artist.id)
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    private fun loadArtist(artistId: Long) {
        viewModelScope.launch {
            runCatching {
                launch {
                    musicRepository.getSongsByArtistId(artistId).collect { _songs.value = it }
                }
                launch {
                    musicRepository.getAlbumsByArtistId(artistId).collect { _albums.value = it }
                }
                launch {
                    _artistImageUri.value = artistRepository.getArtistImageLarge(artistId)
                }
                launch {
                    _artistBiography.value = artistRepository.getArtistBiography(artistId)
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
