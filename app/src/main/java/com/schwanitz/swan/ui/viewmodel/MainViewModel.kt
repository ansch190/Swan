package com.schwanitz.swan.ui.viewmodel

import android.app.Application
import com.schwanitz.swan.util.Logger
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.data.worker.MusicScanWorker
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {

    private val _musicFiles = MutableStateFlow<List<MusicFile>>(emptyList())
    val musicFiles: StateFlow<List<MusicFile>> = _musicFiles.asStateFlow()

    private val _scanProgress = MutableStateFlow<MusicRepository.ScanProgress?>(null)
    val scanProgress: StateFlow<MusicRepository.ScanProgress?> = _scanProgress.asStateFlow()
    var musicService: com.schwanitz.swan.service.MusicPlaybackService? = null
    private val TAG = "MainViewModel"

    init {
        viewModelScope.launch {
            repository.observeAllMusicFiles().collectLatest { files ->
                Logger.d(TAG, "Updated music files from database, total: ${files.size}")
                _musicFiles.value = files
            }
        }

        viewModelScope.launch {
            val filters = repository.getFiltersOnce()
            if (filters.isEmpty()) {
                Logger.d(TAG, "No filters found, adding default filters: Title, Artist, Album")
                val app = getApplication<Application>()
                repository.insertFilter(com.schwanitz.swan.data.local.entity.FilterEntity("title", app.getString(R.string.filter_by_title)))
                repository.insertFilter(com.schwanitz.swan.data.local.entity.FilterEntity("artist", app.getString(R.string.filter_by_artist)))
                repository.insertFilter(com.schwanitz.swan.data.local.entity.FilterEntity("album", app.getString(R.string.filter_by_album)))
            }
            repository.deleteFilter("discNumber")
            repository.deleteFilter("trackNumber")
            Logger.d(TAG, "Removed discNumber and trackNumber filters from database")
        }
    }

    fun addLibraryPath(uri: String, displayName: String): UUID {
        Logger.d(TAG, "Adding library path: $uri")

        val workRequest = OneTimeWorkRequestBuilder<MusicScanWorker>()
            .setInputData(
                workDataOf(
                    MusicScanWorker.KEY_URI to uri,
                    MusicScanWorker.KEY_DISPLAY_NAME to displayName
                )
            )
            .build()

        val workManager = WorkManager.getInstance(getApplication())
        workManager.enqueue(workRequest)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    val scannedFiles = workInfo.progress.getInt(MusicScanWorker.KEY_PROGRESS_SCANNED, 0)
                    val totalFiles = workInfo.progress.getInt(MusicScanWorker.KEY_PROGRESS_TOTAL, 0)
                    if (scannedFiles > 0 || totalFiles > 0) {
                        _scanProgress.value = MusicRepository.ScanProgress(scannedFiles, totalFiles)
                    }
                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        _scanProgress.value = null
                        Logger.d(TAG, "Scan completed successfully for path: $uri")
                        viewModelScope.launch {
                            val files = repository.getAllMusicFilesOnce()
                            _musicFiles.value = files
                            Logger.d(TAG, "Forced UI update after scan, files: ${_musicFiles.value?.size}")
                        }
                    } else if (workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED) {
                        _scanProgress.value = null
                        Logger.d(TAG, "Scan failed or cancelled for path: $uri")
                        cleanupCancelledScan(uri)
                    }
                }
            }
        }
        return workRequest.id
    }

    fun resetScanProgress() {
        _scanProgress.value = null
    }

    fun cleanupCancelledScan(libraryPathUri: String) {
        viewModelScope.launch {
            Logger.d(TAG, "Cleaning up cancelled scan for path: $libraryPathUri")
            repository.deleteFilesByPath(libraryPathUri)
            repository.deleteLibraryPath(libraryPathUri)
            Logger.d(TAG, "Deleted files and path for cancelled scan: $libraryPathUri")
        }
    }

    suspend fun removeLibraryPath(uri: String) {
        Logger.d(TAG, "Removing library path: $uri")
        repository.deleteLibraryPath(uri)
    }

    suspend fun addFilter(criterion: String, displayName: String) {
        Logger.d(TAG, "Adding filter: $criterion")
        repository.insertFilter(com.schwanitz.swan.data.local.entity.FilterEntity(criterion, displayName))
    }

    suspend fun removeFilter(criterion: String): Boolean {
        Logger.d(TAG, "Attempting to remove filter: $criterion")
        val filters = repository.getFiltersOnce()
        return if (filters.size > 1) {
            repository.deleteFilter(criterion)
            Logger.d(TAG, "Filter removed: $criterion")
            true
        } else {
            Logger.w(TAG, "Cannot remove filter: $criterion, at least one filter must remain")
            false
        }
    }

    suspend fun createPlaylist(name: String, songUris: List<String>) {
        Logger.d(TAG, "Creating playlist: $name with ${songUris.size} songs")
        val playlistId = UUID.randomUUID().toString()
        val playlist = com.schwanitz.swan.data.local.entity.PlaylistEntity(
            id = playlistId,
            name = name,
            createdAt = System.currentTimeMillis()
        )
        repository.insertPlaylist(playlist)
        if (songUris.isNotEmpty()) {
            val playlistSongs = songUris.mapIndexed { index, uri ->
                PlaylistSongEntity(
                    id = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    songUri = uri,
                    position = index
                )
            }
            repository.insertPlaylistSongs(playlistSongs)
        }
        Logger.d(TAG, "Playlist created: $name, id: $playlistId")
    }

    suspend fun addSongToPlaylist(playlistId: String, songUri: String) {
        Logger.d(TAG, "Adding song $songUri to playlist $playlistId")
        val currentSongs = repository.getSongsForPlaylist(playlistId)
        val nextPosition = currentSongs.maxOfOrNull { it.position }?.plus(1) ?: 0
        val playlistSong = PlaylistSongEntity(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            songUri = songUri,
            position = nextPosition
        )
        repository.insertPlaylistSongs(listOf(playlistSong))
        Logger.d(TAG, "Added song $songUri to playlist $playlistId at position $nextPosition")
    }

    fun addSongToPlaylistWithResult(playlistId: String, songUri: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                addSongToPlaylist(playlistId, songUri)
                Logger.d(TAG, "Song $songUri added to playlist $playlistId")
                onResult(true)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to add song to playlist: ${e.message}", e)
                onResult(false)
            }
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songUris: List<String>) {
        Logger.d(TAG, "Adding ${songUris.size} songs to playlist $playlistId")
        val currentSongs = repository.getSongsForPlaylist(playlistId)
        val nextPosition = currentSongs.maxOfOrNull { it.position }?.plus(1) ?: 0
        val playlistSongs = songUris.mapIndexed { index, uri ->
            PlaylistSongEntity(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                songUri = uri,
                position = nextPosition + index
            )
        }
        repository.insertPlaylistSongs(playlistSongs)
        Logger.d(TAG, "Added ${songUris.size} songs to playlist $playlistId starting at position $nextPosition")
    }

    suspend fun deletePlaylist(playlistId: String) {
        Logger.d(TAG, "Deleting playlist: $playlistId")
        repository.deletePlaylist(playlistId)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        Logger.d(TAG, "Renaming playlist $playlistId to $newName")
        val playlist = repository.getPlaylistById(playlistId)
        if (playlist != null) {
            val updatedPlaylist = playlist.copy(name = newName)
            repository.updatePlaylist(updatedPlaylist)
            Logger.d(TAG, "Playlist renamed: $playlistId -> $newName")
        } else {
            Logger.w(TAG, "Playlist not found for ID: $playlistId")
        }
    }

    suspend fun updatePlaylistSongOrder(playlistId: String, songs: List<PlaylistSongEntity>) {
        Logger.d(TAG, "Updating playlist song order for playlist $playlistId")
        val existingSongs = repository.getSongsForPlaylist(playlistId)
        existingSongs.forEach { song ->
            repository.deletePlaylistSong(song.id)
        }
        repository.insertPlaylistSongs(songs)
        Logger.d(TAG, "Playlist song order updated for playlist $playlistId")
    }
}
