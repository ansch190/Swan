package com.schwanitz.swan

import android.content.Context
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val context: Context,
    private val repository: MusicRepository,
    private val db: AppDatabase
) : ViewModel() {

    val musicFiles = MutableLiveData<List<MusicFile>>()
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

    suspend fun addLibraryPath(uri: String, displayName: String) {
        Log.d(TAG, "Adding library path: $uri")
        db.libraryPathDao().insertPath(LibraryPathEntity(uri, displayName))
        repository.scanAndStoreMusicFiles(uri, db)
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