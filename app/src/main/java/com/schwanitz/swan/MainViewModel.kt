package com.schwanitz.swan

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val context: Context,
    private val repository: MusicRepository,
    private val db: AppDatabase
) : ViewModel() {

    val musicFiles = MutableLiveData<List<MusicFile>>()
    val scanProgress = MutableLiveData<MusicRepository.ScanProgress?>(null)
    private val TAG = "MainViewModel"

    init {
        // Beobachte Datenbankänderungen
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
    }

    fun addLibraryPath(uri: String, displayName: String) {
        Log.d(TAG, "Adding library path: $uri")
        viewModelScope.launch {
            db.libraryPathDao().insertPath(LibraryPathEntity(uri, displayName))
        }

        // Starte WorkManager-Job
        val workRequest = OneTimeWorkRequestBuilder<MusicScanWorker>()
            .setInputData(
                workDataOf(MusicScanWorker.KEY_URI to uri)
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
                if (workInfo.state == WorkInfo.State.SUCCEEDED || workInfo.state == WorkInfo.State.FAILED) {
                    scanProgress.value = null // Fortschritt zurücksetzen
                    Log.d(TAG, "Scan completed with state: ${workInfo.state}")
                }
            }
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

    suspend fun removeFilter(criterion: String) {
        Log.d(TAG, "Removing filter: $criterion")
        db.filterDao().deleteFilter(criterion)
    }
}