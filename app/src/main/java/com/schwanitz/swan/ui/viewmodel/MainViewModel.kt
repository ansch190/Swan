package com.schwanitz.swan.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.FilterEntity
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

    val musicFiles = MutableLiveData<List<MusicFile>>()
    val scanProgress = MutableLiveData<MusicRepository.ScanProgress?>(null)
    private val TAG = "MainViewModel"

    init {
        // Beobachte Datenbankänderungen für Musikdateien
        viewModelScope.launch {
            db.musicFileDao().getAllFiles().collectLatest { entities ->
                val files = entities.map { entity ->
                    MusicFile(
                        uri = android.net.Uri.parse(entity.uri),
                        name = entity.name,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        albumArtist = entity.albumArtist,
                        discNumber = entity.discNumber,
                        trackNumber = entity.trackNumber,
                        year = entity.year,
                        genre = entity.genre,
                        fileSize = entity.fileSize,
                        audioCodec = entity.audioCodec,
                        sampleRate = entity.sampleRate,
                        bitrate = entity.bitrate,
                        tagVersion = entity.tagVersion
                    )
                }
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

        // Beobachte Fortschritt
        workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
            if (workInfo != null) {
                val scannedFiles = workInfo.progress.getInt(MusicScanWorker.KEY_PROGRESS_SCANNED, 0)
                val totalFiles = workInfo.progress.getInt(MusicScanWorker.KEY_PROGRESS_TOTAL, 0)
                if (scannedFiles > 0 || totalFiles > 0) {
                    scanProgress.value = MusicRepository.ScanProgress(scannedFiles, totalFiles)
                }
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    scanProgress.value = null // Fortschritt zurücksetzen
                    Log.d(TAG, "Scan completed successfully for path: $uri")
                    // Erzwinge UI-Aktualisierung
                    viewModelScope.launch {
                        val files = db.musicFileDao().getAllFiles().first()
                        musicFiles.value = files.map { entity ->
                            MusicFile(
                                uri = android.net.Uri.parse(entity.uri),
                                name = entity.name,
                                title = entity.title,
                                artist = entity.artist,
                                album = entity.album,
                                albumArtist = entity.albumArtist,
                                discNumber = entity.discNumber,
                                trackNumber = entity.trackNumber,
                                year = entity.year,
                                genre = entity.genre,
                                fileSize = entity.fileSize,
                                audioCodec = entity.audioCodec,
                                sampleRate = entity.sampleRate,
                                bitrate = entity.bitrate,
                                tagVersion = entity.tagVersion
                            )
                        }
                        Log.d(TAG, "Forced UI update after scan, files: ${musicFiles.value?.size}")
                    }
                } else if (workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED) {
                    scanProgress.value = null // Fortschritt zurücksetzen
                    Log.d(TAG, "Scan failed or cancelled for path: $uri")
                    cleanupCancelledScan(uri)
                }
            }
        }
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
        // Ermittle die aktuelle höchste Position
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

        // Alte Songs löschen
        val existingSongs = db.playlistDao().getSongsForPlaylist(playlistId)
        existingSongs.forEach { song ->
            db.playlistDao().deletePlaylistSong(song.id)
        }

        // Neue Songs einfügen
        db.playlistDao().insertPlaylistSongs(songs)

        Log.d(TAG, "Playlist song order updated for playlist $playlistId")
    }

}