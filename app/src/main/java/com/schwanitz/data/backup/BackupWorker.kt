package com.schwanitz.data.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.schwanitz.MainActivity
import com.schwanitz.R
import com.schwanitz.data.repository.LibraryOperationCoordinator
import com.schwanitz.data.source.SourceScanCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.crypto.AEADBadTagException

class BackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    private val dependencies = EntryPointAccessors.fromApplication(
        appContext,
        BackupWorkerEntryPoint::class.java,
    )
    private var lastUpdateMillis = 0L
    private var lastStage: BackupJobStage? = null

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return Result.failure()
        val operation = inputData.getString(KEY_OPERATION)?.let(BackupOperation::valueOf)
            ?: return Result.failure()
        var terminal = false
        var completedSuccessfully = false

        report(BackupJobProgress(operation, BackupJobStage.PREPARING), force = true)
        return try {
            val password = dependencies.secretStore().load(jobId)
            when (operation) {
                BackupOperation.EXPORT -> {
                    report(BackupJobProgress(operation, BackupJobStage.WAITING_FOR_SCANS), force = true)
                    dependencies.libraryOperations().withExport {
                    dependencies.backupManager().exportTo(
                        contentResolver = applicationContext.contentResolver,
                        uri = uri,
                        password = password,
                        options = BackupOptions(
                            hideCredentialsAfterRestore = inputData.getBoolean(KEY_HIDE_CREDENTIALS, false),
                            includeLibrary = inputData.getBoolean(KEY_INCLUDE_LIBRARY, false),
                        ),
                        onProgress = ::report,
                    )
                    }
                }

                BackupOperation.RESTORE -> dependencies.libraryOperations().withRestore(
                    onRequested = {
                        report(BackupJobProgress(operation, BackupJobStage.WAITING_FOR_SCANS), force = true)
                        dependencies.scanCoordinator().cancelAll()
                    },
                ) {
                    dependencies.backupManager().importAndRestore(
                        applicationContext.contentResolver,
                        uri,
                        password,
                    ) { report(it.asJobProgress()) }
                }
            }
            terminal = true
            completedSuccessfully = true
            showFinishedNotification(operation, success = true)
            Result.success(resultData(operation, ERROR_NONE, uri))
        } catch (badPassword: AEADBadTagException) {
            terminal = true
            Timber.w("Backup restore rejected an invalid password")
            showFinishedNotification(operation, success = false, wrongPassword = true)
            Result.failure(resultData(operation, ERROR_WRONG_PASSWORD, uri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            terminal = true
            Timber.e(error, "Backup %s failed", operation)
            if (operation == BackupOperation.EXPORT) removePartialDocument(uri)
            val failure = if (operation == BackupOperation.EXPORT) {
                classifyBackupExportFailure(error, lastStage)
            } else null
            showFinishedNotification(operation, success = false, failure = failure)
            Result.failure(
                resultData(
                    operation = operation,
                    error = ERROR_FAILED,
                    uri = uri,
                    failureCode = failure?.first,
                    failureDetail = failure?.second,
                )
            )
        } finally {
            if (terminal) dependencies.secretStore().delete(jobId)
            if (terminal && completedSuccessfully) releaseUriPermission(uri, operation)
        }
    }

    private fun report(progress: BackupJobProgress, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val complete = progress.fraction?.let { it >= 1f } == true
        val stageChanged = progress.stage != lastStage
        if (!force && !stageChanged && !complete && now - lastUpdateMillis < UPDATE_INTERVAL_MS) return
        lastUpdateMillis = now
        lastStage = progress.stage
        setProgressAsync(progress.toData())
        setForegroundAsync(createForegroundInfo(progress))
    }

    private fun createForegroundInfo(progress: BackupJobProgress): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(applicationContext.getString(progress.operation.titleRes()))
            .setContentText(progressText(progress))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                PROGRESS_MAX,
                ((progress.fraction ?: 0f) * PROGRESS_MAX).toInt(),
                progress.fraction == null,
            )
            .apply {
                if (progress.operation == BackupOperation.EXPORT) {
                    addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        applicationContext.getString(R.string.backup_notification_cancel),
                        WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
                    )
                }
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID_RUNNING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_RUNNING, notification)
        }
    }

    private fun showFinishedNotification(
        operation: BackupOperation,
        success: Boolean,
        wrongPassword: Boolean = false,
        failure: Pair<BackupFailureCode, String?>? = null,
    ) {
        ensureChannel()
        val textRes = when {
            success && operation == BackupOperation.EXPORT -> R.string.backup_notification_export_success
            success -> R.string.backup_notification_restore_success
            wrongPassword -> R.string.backup_restore_wrong_password
            operation == BackupOperation.EXPORT -> R.string.backup_notification_export_failed
            else -> R.string.backup_notification_restore_failed
        }
        val text = failure?.let { (code, detail) -> exportFailureText(code, detail) }
            ?: applicationContext.getString(textRes)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
            .setContentTitle(applicationContext.getString(operation.titleRes()))
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID_FINISHED, notification)
    }

    private fun progressText(progress: BackupJobProgress): String {
        val stage = applicationContext.getString(progress.stage.stringRes(progress.operation))
        val items = if (progress.completedItems != null && progress.totalItems != null) {
            applicationContext.getString(
                R.string.backup_notification_items_progress,
                progress.completedItems,
                progress.totalItems,
            )
        } else null
        val bytes = progress.totalBytes?.takeIf { it > 0 }?.let { total ->
            applicationContext.getString(
                R.string.backup_restore_bytes_progress,
                Formatter.formatShortFileSize(applicationContext, progress.completedBytes),
                Formatter.formatShortFileSize(applicationContext, total),
            )
        }
        val summary = if (progress.operation == BackupOperation.EXPORT && progress.sourceCount != null) {
            if (progress.songCount != null && progress.imageCount != null) {
                applicationContext.getString(
                    R.string.backup_export_summary_library,
                    progress.sourceCount,
                    progress.songCount,
                    progress.imageCount,
                )
            } else applicationContext.getString(
                R.string.backup_export_summary_sources,
                progress.sourceCount,
            )
        } else null
        return listOfNotNull(stage, bytes, items, summary).joinToString(" · ")
    }

    private fun ensureChannel() {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.backup_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_BACKUP, true)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun removePartialDocument(uri: Uri) {
        runCatching {
            if (!DocumentsContract.deleteDocument(applicationContext.contentResolver, uri)) {
                applicationContext.contentResolver.openOutputStream(uri, "wt")?.close()
            }
        }.onFailure {
            runCatching { applicationContext.contentResolver.openOutputStream(uri, "wt")?.close() }
        }
    }

    private fun releaseUriPermission(uri: Uri, operation: BackupOperation) {
        val flag = if (operation == BackupOperation.EXPORT) Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        else Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { applicationContext.contentResolver.releasePersistableUriPermission(uri, flag) }
    }

    private fun BackupJobProgress.toData() = workDataOf(
        KEY_OPERATION to operation.name,
        KEY_STAGE to stage.name,
        KEY_COMPLETED_BYTES to completedBytes,
        KEY_TOTAL_BYTES to (totalBytes ?: -1L),
        KEY_COMPLETED_ITEMS to (completedItems ?: -1),
        KEY_TOTAL_ITEMS to (totalItems ?: -1),
        KEY_SOURCE_COUNT to (sourceCount ?: -1),
        KEY_SONG_COUNT to (songCount ?: -1),
        KEY_IMAGE_COUNT to (imageCount ?: -1),
    )

    private fun resultData(
        operation: BackupOperation,
        error: String,
        uri: Uri,
        failureCode: BackupFailureCode? = null,
        failureDetail: String? = null,
    ) = workDataOf(
        KEY_OPERATION to operation.name,
        KEY_ERROR to error,
        KEY_FAILURE_CODE to failureCode?.name,
        KEY_FAILURE_DETAIL to failureDetail?.take(MAX_FAILURE_DETAIL_LENGTH),
        KEY_URI to uri.toString(),
        KEY_REQUESTED_AT to inputData.getLong(KEY_REQUESTED_AT, 0L),
    )

    private fun exportFailureText(code: BackupFailureCode, detail: String?): String = when (code) {
        BackupFailureCode.DESTINATION_UNAVAILABLE -> applicationContext.getString(R.string.backup_export_error_destination)
        BackupFailureCode.LIBRARY_READ_FAILED -> applicationContext.getString(R.string.backup_export_error_library)
        BackupFailureCode.SOURCE_MISMATCH -> applicationContext.getString(R.string.backup_export_error_sources)
        BackupFailureCode.ASSET_UNAVAILABLE -> applicationContext.getString(
            R.string.backup_export_error_asset,
            detail.orEmpty(),
        )
        BackupFailureCode.FINALIZATION_FAILED -> applicationContext.getString(R.string.backup_export_error_finalizing)
        BackupFailureCode.VERIFICATION_FAILED -> applicationContext.getString(R.string.backup_export_error_verification)
        BackupFailureCode.UNKNOWN -> applicationContext.getString(R.string.backup_notification_export_failed)
    }

    companion object {
        const val TAG = "swan.backup.job"
        const val UNIQUE_WORK_NAME = "swan-backup-job"
        const val KEY_JOB_ID = "job_id"
        const val KEY_URI = "uri"
        const val KEY_OPERATION = "operation"
        const val KEY_HIDE_CREDENTIALS = "hide_credentials"
        const val KEY_INCLUDE_LIBRARY = "include_library"
        const val KEY_REQUESTED_AT = "requested_at"
        const val KEY_STAGE = "stage"
        const val KEY_COMPLETED_BYTES = "completed_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_COMPLETED_ITEMS = "completed_items"
        const val KEY_TOTAL_ITEMS = "total_items"
        const val KEY_SOURCE_COUNT = "source_count"
        const val KEY_SONG_COUNT = "song_count"
        const val KEY_IMAGE_COUNT = "image_count"
        const val KEY_ERROR = "error"
        const val KEY_FAILURE_CODE = "failure_code"
        const val KEY_FAILURE_DETAIL = "failure_detail"
        const val ERROR_NONE = ""
        const val ERROR_WRONG_PASSWORD = "wrong_password"
        const val ERROR_FAILED = "failed"
        private const val CHANNEL_ID = "backup_jobs"
        private const val NOTIFICATION_ID_RUNNING = 30_001
        private const val NOTIFICATION_ID_FINISHED = 30_002
        private const val UPDATE_INTERVAL_MS = 500L
        private const val PROGRESS_MAX = 10_000
        private const val MAX_FAILURE_DETAIL_LENGTH = 300
    }
}

private fun BackupOperation.titleRes() = when (this) {
    BackupOperation.EXPORT -> R.string.backup_notification_export_title
    BackupOperation.RESTORE -> R.string.backup_notification_restore_title
}

internal fun BackupJobStage.stringRes(operation: BackupOperation) = when (this) {
    BackupJobStage.PREPARING -> R.string.backup_job_stage_preparing
    BackupJobStage.PREPARING_KEY -> R.string.backup_restore_stage_preparing
    BackupJobStage.WRITING_CONFIGURATION -> R.string.backup_job_stage_configuration
    BackupJobStage.EXPORTING_LIBRARY -> R.string.backup_job_stage_export_library
    BackupJobStage.EXPORTING_ASSETS -> R.string.backup_job_stage_export_assets
    BackupJobStage.FINALIZING -> if (operation == BackupOperation.EXPORT) {
        R.string.backup_job_stage_export_finalizing
    } else R.string.backup_restore_stage_finalizing
    BackupJobStage.DECRYPTING -> R.string.backup_restore_stage_decrypting
    BackupJobStage.VALIDATING -> R.string.backup_restore_stage_validating
    BackupJobStage.EXTRACTING_ASSETS -> R.string.backup_restore_stage_assets
    BackupJobStage.RESTORING_LIBRARY -> R.string.backup_restore_stage_library
    BackupJobStage.APPLYING_SETTINGS -> R.string.backup_restore_stage_settings
    BackupJobStage.WAITING_FOR_SCANS -> R.string.backup_job_stage_waiting_scans
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun backupManager(): BackupManager
    fun secretStore(): BackupJobSecretStore
    fun scanCoordinator(): SourceScanCoordinator
    fun libraryOperations(): LibraryOperationCoordinator
}
