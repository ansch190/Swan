package com.schwanitz.data.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.schwanitz.R
import com.schwanitz.domain.repository.SourceLifecycleManager
import com.schwanitz.domain.repository.SourceRefreshResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import timber.log.Timber

class SourceScanWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    private val dependencies = EntryPointAccessors.fromApplication(
        appContext,
        SourceScanWorkerEntryPoint::class.java,
    )

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        val sourceName = inputData.getString(KEY_SOURCE_NAME).orEmpty().ifBlank { sourceId }
        val requestedAt = inputData.getLong(KEY_REQUESTED_AT, 0L)

        setProgress(progressData(sourceId, sourceName, requestedAt, 0, 0))
        setForeground(createForegroundInfo(sourceId, sourceName))

        return try {
            when (val result = dependencies.sourceLifecycleManager().refreshSource(sourceId) { scanned, total ->
                setProgressAsync(progressData(sourceId, sourceName, requestedAt, scanned, total))
            }) {
                is SourceRefreshResult.Success -> Result.success(
                    workDataOf(
                        KEY_SOURCE_ID to sourceId,
                        KEY_SOURCE_NAME to sourceName,
                        KEY_REQUESTED_AT to requestedAt,
                        KEY_TOTAL to result.total,
                        KEY_UPDATED to result.updated,
                        KEY_RETAINED_FAILURES to result.retainedFailures,
                    )
                )

                is SourceRefreshResult.Failure -> Result.failure(
                    workDataOf(
                        KEY_SOURCE_ID to sourceId,
                        KEY_SOURCE_NAME to sourceName,
                        KEY_REQUESTED_AT to requestedAt,
                        KEY_ERROR to result.error.message,
                    )
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Timber.e(error, "Background scan failed for %s", sourceName)
            Result.failure(
                workDataOf(
                    KEY_SOURCE_ID to sourceId,
                    KEY_SOURCE_NAME to sourceName,
                    KEY_REQUESTED_AT to requestedAt,
                    KEY_ERROR to (error.message ?: error.javaClass.simpleName),
                )
            )
        }
    }

    private fun createForegroundInfo(sourceId: String, sourceName: String): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                applicationContext.getString(R.string.scan_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(R.string.scan_notification_title))
            .setContentText(applicationContext.getString(R.string.scan_notification_text, sourceName))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        val notificationId = NOTIFICATION_ID_BASE + (sourceId.hashCode() and Int.MAX_VALUE) % 10_000
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun progressData(
        sourceId: String,
        sourceName: String,
        requestedAt: Long,
        scanned: Int,
        total: Int,
    ) = workDataOf(
        KEY_SOURCE_ID to sourceId,
        KEY_SOURCE_NAME to sourceName,
        KEY_REQUESTED_AT to requestedAt,
        KEY_SCANNED to scanned,
        KEY_TOTAL to total,
    )

    companion object {
        const val TAG = "swan.source.scan"
        const val SOURCE_TAG_PREFIX = "swan.source.scan.id:"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_REQUESTED_AT = "requested_at"
        const val KEY_SCANNED = "scanned"
        const val KEY_TOTAL = "total"
        const val KEY_UPDATED = "updated"
        const val KEY_RETAINED_FAILURES = "retained_failures"
        const val KEY_ERROR = "error"
        private const val NOTIFICATION_CHANNEL_ID = "library_scans"
        private const val NOTIFICATION_ID_BASE = 20_000
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SourceScanWorkerEntryPoint {
    fun sourceLifecycleManager(): SourceLifecycleManager
}
