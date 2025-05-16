package com.schwanitz.swan.data.worker

import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.LibraryPathEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import kotlinx.coroutines.flow.collect

class MusicScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_URI = "uri"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_PROGRESS_SCANNED = "progress_scanned"
        const val KEY_PROGRESS_TOTAL = "progress_total"
    }

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: return Result.failure()
        val repository = MusicRepository(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)

        return try {
            db.withTransaction {
                // Füge den Pfad hinzu
                db.libraryPathDao().insertPath(LibraryPathEntity(uri, displayName))

                // Führe den Scan durch und speichere Dateien
                repository.scanAndStoreMusicFiles(uri, db).collect { progress ->
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS_SCANNED to progress.scannedFiles,
                            KEY_PROGRESS_TOTAL to progress.totalFiles
                        )
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            // Bei einem Fehler wird die Transaktion rückgängig gemacht
            Result.failure()
        }
    }
}