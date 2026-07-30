package com.schwanitz.ui.screens.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.R
import com.schwanitz.data.local.dao.PlaylistSongWithMapping
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.schwanitz.ui.common.ErrorHolder
import com.schwanitz.ui.common.toggleFavorite
import com.schwanitz.ui.components.SelectionDelegate
import javax.inject.Inject

private const val FAVORITES_PLAYLIST_ID = -1L

sealed class PendingRemoval {
    data class RemoveOne(val mappingId: Long, val songId: String) : PendingRemoval()
    data class RemoveAll(val songId: String) : PendingRemoval()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerManager: MusicPlayerManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val errorHolder = ErrorHolder()

    private val _playlistId = MutableStateFlow<Long?>(null)

    val isFavoritesPlaylist: StateFlow<Boolean> = _playlistId
        .map { it == FAVORITES_PLAYLIST_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playlistName: StateFlow<String> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            if (id == FAVORITES_PLAYLIST_ID) {
                flowOf(context.getString(R.string.playlist_favorites_name))
            } else {
                playlistRepository.getPlaylistName(id).map { it ?: context.getString(R.string.playlist_default_name) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), context.getString(R.string.playlist_default_name))

    val songs: StateFlow<List<PlaylistSongWithMapping>> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            if (id == FAVORITES_PLAYLIST_ID) {
                songRepository.getFavoriteSongs().map { list ->
                    list.mapIndexed { index, song ->
                        PlaylistSongWithMapping(
                            mappingId = -(index.toLong() + 1),
                            id = song.id,
                            title = song.title,
                            artistId = song.artistId,
                            artistName = song.artistName,
                            albumId = song.albumId,
                            albumName = song.albumName,
                            albumArtistName = song.albumArtistName,
                            durationMs = song.durationMs,
                            albumArtUri = song.albumArtUri,
                            albumArtUriLarge = song.albumArtUriLarge,
                            sourceId = song.sourceId,
                            isFavorite = song.isFavorite,
                            isActive = song.isActive,
                            discNumber = song.discNumber,
                            trackNumber = song.trackNumber,
                            year = song.year,
                            genre = song.genre,
                            mimeType = song.mimeType,
                            sampleRate = song.sampleRate,
                            bitrate = song.bitrate,
                            fileSize = song.fileSize,
                            tagVersion = song.tagVersion
                        )
                    }
                }
            } else {
                playlistRepository.getPlaylistSongsWithMapping(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadPlaylist(playlistId: Long) {
        _playlistId.value = playlistId
    }

    fun renamePlaylist(newName: String) {
        viewModelScope.launch {
            runCatching {
                _playlistId.value?.let { id ->
                    if (id != FAVORITES_PLAYLIST_ID) {
                        playlistRepository.renamePlaylist(id, newName)
                    }
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun playSong(entry: PlaylistSongWithMapping) {
        playerManager.play(entry.toSong(), listOf(entry.toSong()))
    }

    fun playAll() {
        val allSongs = songs.value.map { it.toSong() }
        val first = allSongs.firstOrNull() ?: return
        playerManager.play(first, allSongs)
    }

    fun toggleFavorite(entry: PlaylistSongWithMapping) = toggleFavorite(entry.toSong(), songRepository, errorHolder)

    private val selection = SelectionDelegate(playerManager, playlistRepository, viewModelScope, { songs.value.map { it.toSong() } }, errorHolder, context)
    val isSelecting: StateFlow<Boolean> = selection.isSelecting
    val selectedSongIds: StateFlow<Set<String>> = selection.selectedSongIds

    fun enterSelection(entry: PlaylistSongWithMapping) = selection.enterSelection(entry.toSong())
    fun toggleSelection(songId: String) = selection.toggleSelection(songId)
    fun playSelection() = selection.playSelection()
    fun addSelectionToQueue() = selection.addSelectionToQueue()
    fun getSelectedSongIds(): String = selection.getSelectedSongIds()

    private val _pendingSongAdditions = MutableStateFlow<List<Song>>(emptyList())
    val pendingSongAdditions: StateFlow<List<Song>> = _pendingSongAdditions

    fun queueSongAdditions(songs: List<Song>) {
        _pendingSongAdditions.value = songs
    }

    fun clearPendingAdditions() {
        _pendingSongAdditions.value = emptyList()
    }

    fun savePlaylistChanges(
        orderedSongIds: List<String>,
        removals: List<PendingRemoval>,
        additions: List<String>
    ) {
        viewModelScope.launch {
            runCatching {
                val pid = _playlistId.value ?: return@launch
                if (pid == FAVORITES_PLAYLIST_ID) return@launch
                for (removal in removals) {
                    when (removal) {
                        is PendingRemoval.RemoveOne -> playlistRepository.removeOneSongFromPlaylist(removal.mappingId)
                        is PendingRemoval.RemoveAll -> playlistRepository.removeAllSongsFromPlaylist(pid, removal.songId)
                    }
                }
                var order = playlistRepository.getPlaylistSongCount(pid) - additions.size
                for (songId in additions) {
                    playlistRepository.addSongToPlaylist(pid, songId, order)
                    order++
                }
                val updatedSongs = playlistRepository.getPlaylistSongsWithMapping(pid).first()
                val realMappingIds = orderedSongIds.mapNotNull { sid ->
                    updatedSongs.find { it.id == sid }?.mappingId
                }
                playlistRepository.reorderSongs(realMappingIds)
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    private fun PlaylistSongWithMapping.toSong(): Song = Song(
        id = id,
        title = title,
        artistId = artistId,
        artistName = artistName ?: "",
        albumId = albumId,
        albumName = albumName ?: "",
        albumArtistName = albumArtistName ?: "",
        durationMs = durationMs,
        albumArtUri = albumArtUri,
        albumArtUriLarge = albumArtUriLarge,
        sourceId = sourceId,
        isFavorite = isFavorite,
        isActive = isActive,
        discNumber = discNumber,
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        mimeType = mimeType,
        sampleRate = sampleRate,
        bitrate = bitrate,
        fileSize = fileSize,
        tagVersion = tagVersion
    )
}
