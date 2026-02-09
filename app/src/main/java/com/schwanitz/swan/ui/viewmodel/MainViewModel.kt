package com.schwanitz.swan.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.FilterEntity
import com.schwanitz.swan.data.local.entity.MusicFileEntity
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.data.worker.MusicScanWorker
import com.schwanitz.swan.domain.model.MusicFile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    private val context: Context,
    private val repository: MusicRepository,
    private val db: AppDatabase
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    val musicFiles = MutableLiveData<List<MusicFile>>()
    val scanProgress = MutableLiveData<MusicRepository.ScanProgress?>(null)

    init {
        // Beobachte Datenbankänderungen für Musikdateien
        viewModelScope.launch {
            db.musicFileDao().getAllFiles().collectLatest { entities ->
                val files = entities.map { it.toDomainModel() }
                Log.d(TAG, "Updated music files from database, total: ${files.size}")
                musicFiles.value = files
            }
        }

        // Initialisiere Standardfilter, falls keine Filter vorhanden sind
        viewModelScope.launch {
            val filters = db.filterDao().getAllFilters().first()
            if (filters.isEmpty()) {
                Log.d(TAG, "No filters found, adding default filters: Title, Artist, Album")
                db.filterDao().insertFilter(FilterEntity("title", context.getString(R.string.filter_by_title)))
                db.filterDao().insertFilter(FilterEntity("artist", context.getString(R.string.filter_by_artist)))
                db.filterDao().insertFilter(FilterEntity("album", context.getString(R.string.filter_by_album)))
            }
            // Entferne bestehende discNumber und trackNumber Filter
            db.filterDao().deleteFilter("discNumber")
            db.filterDao().deleteFilter("trackNumber")
            Log.d(TAG, "Removed discNumber and trackNumber filters from database")
        }
    }

    fun addLibraryPath(uri: String, displayName: String): UUID {
        Log.d(TAG, "Adding library path: $uri")

        // Starte WorkManager-Job
        val workRequest = OneTimeWorkRequestBuilder<MusicScanWorker>()
            .setInputData(
                workDataOf(
                    MusicScanWorker.KEY_URI to uri,
                    MusicScanWorker.KEY_DISPLAY_NAME to displayName
                )
            )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(workRequest)

        // Beobachte Fortschritt mit selbst-entfernendem Observer
        val workInfoLiveData: LiveData<WorkInfo> = workManager.getWorkInfoByIdLiveData(workRequest.id)
        val observer = object : Observer<WorkInfo> {
            override fun onChanged(value: WorkInfo) {
                val scannedFiles = value.progress.getInt(MusicScanWorker.KEY_PROGRESS_SCANNED, 0)
                val totalFiles = value.progress.getInt(MusicScanWorker.KEY_PROGRESS_TOTAL, 0)
                if (scannedFiles > 0 || totalFiles > 0) {
                    scanProgress.value = MusicRepository.ScanProgress(scannedFiles, totalFiles)
                }
                if (value.state.isFinished) {
                    scanProgress.value = null
                    if (value.state == WorkInfo.State.SUCCEEDED) {
                        Log.d(TAG, "Scan completed successfully for path: $uri")
                        viewModelScope.launch {
                            val files = db.musicFileDao().getAllFiles().first()
                            musicFiles.value = files.map { it.toDomainModel() }
                            Log.d(TAG, "Forced UI update after scan, files: ${musicFiles.value?.size}")
                        }
                    } else {
                        Log.d(TAG, "Scan failed or cancelled for path: $uri")
                        cleanupCancelledScan(uri)
                    }
                    // Observer entfernen, um Memory Leak zu vermeiden
                    workInfoLiveData.removeObserver(this)
                }
            }
        }
        workInfoLiveData.observeForever(observer)

        return workRequest.id
    }

    fun cleanupCancelledScan(libraryPathUri: String) {
        viewModelScope.launch {
            Log.d(TAG, "Cleaning up cancelled scan for path: $libraryPathUri")
            db.musicFileDao().deleteFilesByPath(libraryPathUri)
            db.libraryPathDao().deletePath(libraryPathUri)
            Log.d(TAG, "Deleted files and path for cancelled scan: $libraryPathUri")
        }
    }

    suspend fun removeLibraryPath(uri: String) {
        Log.d(TAG, "Removing library path: $uri")
        db.libraryPathDao().deletePath(uri)
        // Dateien werden durch ForeignKey CASCADE automatisch gelöscht
    }

    suspend fun addFilter(criterion: String, displayName: String) {
        Log.d(TAG, "Adding filter: $criterion")
        db.filterDao().insertFilter(FilterEntity(criterion, displayName))
    }

    suspend fun removeFilter(criterion: String): Boolean {
        Log.d(TAG, "Attempting to remove filter: $criterion")
        val filters = db.filterDao().getAllFilters().first()
        return if (filters.size > 1) {
            db.filterDao().deleteFilter(criterion)
            Log.d(TAG, "Filter removed: $criterion")
            true
        } else {
            Log.w(TAG, "Cannot remove filter: $criterion, at least one filter must remain")
            false
        }
    }

    suspend fun createPlaylist(name: String, songUris: List<String>) {
        Log.d(TAG, "Creating playlist: $name with ${songUris.size} songs")
        val playlistId = UUID.randomUUID().toString()
        val playlist = PlaylistEntity(
            id = playlistId,
            name = name,
            createdAt = System.currentTimeMillis()
        )
        db.playlistDao().insertPlaylist(playlist)
        if (songUris.isNotEmpty()) {
            val playlistSongs = songUris.mapIndexed { index, uri ->
                PlaylistSongEntity(
                    id = UUID.randomUUID().toString(),
                    playlistId = playlistId,
                    songUri = uri,
                    position = index
                )
            }
            db.playlistDao().insertPlaylistSongs(playlistSongs)
        }
        Log.d(TAG, "Playlist created: $name, id: $playlistId")
    }

    suspend fun addSongToPlaylist(playlistId: String, songUri: String) {
        Log.d(TAG, "Adding song $songUri to playlist $playlistId")
        val currentSongs = db.playlistDao().getSongsForPlaylist(playlistId)
        val nextPosition = currentSongs.maxOfOrNull { it.position }?.plus(1) ?: 0
        val playlistSong = PlaylistSongEntity(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            songUri = songUri,
            position = nextPosition
        )
        db.playlistDao().insertPlaylistSongs(listOf(playlistSong))
        Log.d(TAG, "Added song $songUri to playlist $playlistId at position $nextPosition")
    }

    fun addSongToPlaylistWithResult(playlistId: String, songUri: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                addSongToPlaylist(playlistId, songUri)
                Log.d(TAG, "Song $songUri added to playlist $playlistId")
                onResult(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add song to playlist: ${e.message}", e)
                onResult(false)
            }
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songUris: List<String>) {
        Log.d(TAG, "Adding ${songUris.size} songs to playlist $playlistId")
        val currentSongs = db.playlistDao().getSongsForPlaylist(playlistId)
        val nextPosition = currentSongs.maxOfOrNull { it.position }?.plus(1) ?: 0
        val playlistSongs = songUris.mapIndexed { index, uri ->
            PlaylistSongEntity(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                songUri = uri,
                position = nextPosition + index
            )
        }
        db.playlistDao().insertPlaylistSongs(playlistSongs)
        Log.d(TAG, "Added ${songUris.size} songs to playlist $playlistId starting at position $nextPosition")
    }

    suspend fun deletePlaylist(playlistId: String) {
        Log.d(TAG, "Deleting playlist: $playlistId")
        db.playlistDao().deletePlaylist(playlistId)
        // Songs werden durch ForeignKey CASCADE automatisch gelöscht
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        Log.d(TAG, "Renaming playlist $playlistId to $newName")
        val playlist = db.playlistDao().getPlaylistById(playlistId)
        if (playlist != null) {
            val updatedPlaylist = playlist.copy(name = newName)
            db.playlistDao().updatePlaylist(updatedPlaylist)
            Log.d(TAG, "Playlist renamed: $playlistId -> $newName")
        } else {
            Log.w(TAG, "Playlist not found for ID: $playlistId")
        }
    }

    suspend fun updatePlaylistSongOrder(playlistId: String, songs: List<PlaylistSongEntity>) {
        Log.d(TAG, "Updating playlist song order for playlist $playlistId")
        // Batch-Delete statt einzelner Lösch-Aufrufe
        db.playlistDao().deleteAllSongsForPlaylist(playlistId)
        db.playlistDao().insertPlaylistSongs(songs)
        Log.d(TAG, "Playlist song order updated for playlist $playlistId")
    }
}

/** Extension-Funktion für Entity-zu-Domain-Mapping */
fun MusicFileEntity.toDomainModel() = MusicFile(
    uri = android.net.Uri.parse(uri),
    name = name,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    fileSize = fileSize,
    audioCodec = audioCodec,
    sampleRate = sampleRate,
    bitrate = bitrate,
    tagVersion = tagVersion
)
