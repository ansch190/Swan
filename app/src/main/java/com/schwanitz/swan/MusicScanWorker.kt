package com.schwanitz.swan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.collect

class MusicScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_URI = "uri"
        const val KEY_PROGRESS_SCANNED = "progress_scanned"
        const val KEY_PROGRESS_TOTAL = "progress_total"
    }

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI) ?: return Result.failure()
        val repository = MusicRepository(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)

        return try {
            repository.scanAndStoreMusicFiles(uri, db).collect { progress ->
                setProgress(
                    workDataOf(
                        KEY_PROGRESS_SCANNED to progress.scannedFiles,
                        KEY_PROGRESS_TOTAL to progress.totalFiles
                    )
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}