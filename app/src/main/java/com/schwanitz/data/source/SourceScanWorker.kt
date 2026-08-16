package com.schwanitz.data.source

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class SourceScanWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    private val lastNotificationUpdate = AtomicLong(0L)
    private val highestScanned = AtomicInteger(0)

    private val dependencies = EntryPointAccessors.fromApplication(
        appContext,
        SourceScanWorkerEntryPoint::class.java,
    )

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        val sourceName = inputData.getString(KEY_SOURCE_NAME).orEmpty().ifBlank { sourceId }
        val requestedAt = inputData.getLong(KEY_REQUESTED_AT, 0L)

        setProgress(progressData(sourceId, sourceName, requestedAt, 0, 0))
        setForeground(createForegroundInfo(sourceId, sourceName, 0, 0))

        return try {
            when (val result = dependencies.sourceLifecycleManager().refreshSource(sourceId) { scanned, total ->
                val displayedScanned = highestScanned.accumulateAndGet(scanned, ::maxOf)
                setProgressAsync(progressData(sourceId, sourceName, requestedAt, displayedScanned, total))
                updateForegroundProgress(sourceId, sourceName, displayedScanned, total)
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

    private fun updateForegroundProgress(
        sourceId: String,
        sourceName: String,
        scanned: Int,
        total: Int,
    ) {
        val now = System.currentTimeMillis()
        val previous = lastNotificationUpdate.get()
        val isComplete = total > 0 && scanned >= total
        if (!isComplete && now - previous < NOTIFICATION_UPDATE_INTERVAL_MS) return
        if (!lastNotificationUpdate.compareAndSet(previous, now) && !isComplete) return
        setForegroundAsync(createForegroundInfo(sourceId, sourceName, scanned, total))
    }

    private fun createForegroundInfo(
        sourceId: String,
        sourceName: String,
        scanned: Int,
        total: Int,
    ): ForegroundInfo {
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
            .setContentText(
                if (total > 0) {
                    applicationContext.getString(
                        R.string.scan_notification_progress,
                        sourceName,
                        scanned.coerceAtMost(total),
                        total,
                    )
                } else {
                    applicationContext.getString(R.string.scan_notification_text, sourceName)
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                applicationContext.getString(R.string.scan_notification_cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
            )
            .setProgress(
                total.coerceAtLeast(0),
                scanned.coerceIn(0, total.coerceAtLeast(0)),
                total <= 0,
            )
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
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 500L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SourceScanWorkerEntryPoint {
    fun sourceLifecycleManager(): SourceLifecycleManager
}
