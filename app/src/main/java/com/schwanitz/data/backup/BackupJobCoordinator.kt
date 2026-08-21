package com.schwanitz.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class BackupWorkState(
    val workId: String,
    val jobId: String,
    val operation: BackupOperation,
    val state: WorkInfo.State,
    val progress: BackupJobProgress?,
    val error: String?,
    val failureCode: BackupFailureCode?,
    val failureDetail: String?,
    val uri: Uri?,
) {
    val isRunning: Boolean get() = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING
}

class BackupUriPermissionException(cause: Throwable) : Exception(cause)

@Singleton
class BackupJobCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: BackupJobSecretStore,
) {
    private val workManager = WorkManager.getInstance(context)
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _jobRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val jobRequested: SharedFlow<Unit> = _jobRequested.asSharedFlow()

    val workState: Flow<BackupWorkState?> =
        workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.UNIQUE_WORK_NAME).map { infos ->
            val acknowledged = preferences.getStringSet(KEY_ACKNOWLEDGED, emptySet()).orEmpty()
            infos.filterNot { it.state.isFinished && it.id.toString() in acknowledged }
                .maxByOrNull { info -> requestedAt(info) }
                ?.let(::toState)
        }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.UNIQUE_WORK_NAME).collect { infos ->
                val activeJobIds = infos.filterNot { it.state.isFinished }.mapNotNull { info ->
                    info.tags.firstOrNull { it.startsWith(JOB_TAG_PREFIX) }?.removePrefix(JOB_TAG_PREFIX)
                }.toSet()
                secretStore.cleanupExcept(activeJobIds)
                infos.filter { it.state == WorkInfo.State.CANCELLED }.forEach { info ->
                    val state = toState(info)
                    info.tags.firstOrNull { it.startsWith(JOB_TAG_PREFIX) }
                        ?.removePrefix(JOB_TAG_PREFIX)?.let(secretStore::delete)
                    if (state.operation == BackupOperation.EXPORT) state.uri?.let(::removePartialDocument)
                    acknowledge(state)
                }
                cleanupOldTemporaryFiles()
            }
        }
    }

    suspend fun enqueueExport(
        uri: Uri,
        password: String,
        options: BackupOptions,
    ): Boolean = enqueue(BackupOperation.EXPORT, uri, password, options)

    suspend fun enqueueRestore(uri: Uri, password: String): Boolean =
        enqueue(BackupOperation.RESTORE, uri, password, BackupOptions())

    fun cancelExport(workId: String) {
        workManager.cancelWorkById(UUID.fromString(workId))
    }

    fun acknowledge(state: BackupWorkState, releasePermission: Boolean = true) {
        val ids = preferences.getStringSet(KEY_ACKNOWLEDGED, emptySet()).orEmpty() + state.workId
        preferences.edit()
            .putStringSet(KEY_ACKNOWLEDGED, ids.toList().takeLast(MAX_ACKNOWLEDGED).toSet())
            .remove(META_URI_PREFIX + state.workId)
            .apply()
        if (releasePermission) state.uri?.let { releasePermission(it, state.operation) }
    }

    fun releasePermission(uri: Uri, operation: BackupOperation) {
        val flag = if (operation == BackupOperation.EXPORT) Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        else Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, flag) }
    }

    private suspend fun enqueue(
        operation: BackupOperation,
        uri: Uri,
        password: String,
        options: BackupOptions,
    ): Boolean {
        val infos = workManager.getWorkInfosForUniqueWorkFlow(BackupWorker.UNIQUE_WORK_NAME).first()
        if (infos.any { !it.state.isFinished }) return false

        persistPermission(uri, operation)
        val jobId = UUID.randomUUID().toString()
        val requestedAt = System.currentTimeMillis()
        secretStore.cleanupExcept(emptySet())
        secretStore.save(jobId, password)
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(
                workDataOf(
                    BackupWorker.KEY_JOB_ID to jobId,
                    BackupWorker.KEY_URI to uri.toString(),
                    BackupWorker.KEY_OPERATION to operation.name,
                    BackupWorker.KEY_HIDE_CREDENTIALS to options.hideCredentialsAfterRestore,
                    BackupWorker.KEY_INCLUDE_LIBRARY to options.includeLibrary,
                    BackupWorker.KEY_REQUESTED_AT to requestedAt,
                )
            )
            .addTag(BackupWorker.TAG)
            .addTag(OPERATION_TAG_PREFIX + operation.name)
            .addTag(JOB_TAG_PREFIX + jobId)
            .addTag(REQUEST_TAG_PREFIX + requestedAt)
            .build()
        preferences.edit().putString(META_URI_PREFIX + request.id, uri.toString()).apply()
        return try {
            workManager.enqueueUniqueWork(
                BackupWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            _jobRequested.tryEmit(Unit)
            true
        } catch (error: Throwable) {
            secretStore.delete(jobId)
            preferences.edit().remove(META_URI_PREFIX + request.id).apply()
            throw error
        }
    }

    private fun persistPermission(uri: Uri, operation: BackupOperation) {
        val flag = if (operation == BackupOperation.EXPORT) Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        else Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, flag)
        } catch (error: SecurityException) {
            throw BackupUriPermissionException(error)
        }
    }

    private fun toState(info: WorkInfo): BackupWorkState {
        val operation = info.progress.getString(BackupWorker.KEY_OPERATION)?.let(BackupOperation::valueOf)
            ?: info.outputData.getString(BackupWorker.KEY_OPERATION)?.let(BackupOperation::valueOf)
            ?: info.tags.first { it.startsWith(OPERATION_TAG_PREFIX) }
                .removePrefix(OPERATION_TAG_PREFIX).let(BackupOperation::valueOf)
        val stage = info.progress.getString(BackupWorker.KEY_STAGE)?.let(BackupJobStage::valueOf)
        val totalBytes = info.progress.getLong(BackupWorker.KEY_TOTAL_BYTES, -1L).takeIf { it >= 0 }
        val completedItems = info.progress.getInt(BackupWorker.KEY_COMPLETED_ITEMS, -1).takeIf { it >= 0 }
        val totalItems = info.progress.getInt(BackupWorker.KEY_TOTAL_ITEMS, -1).takeIf { it >= 0 }
        val sourceCount = info.progress.getInt(BackupWorker.KEY_SOURCE_COUNT, -1).takeIf { it >= 0 }
        val songCount = info.progress.getInt(BackupWorker.KEY_SONG_COUNT, -1).takeIf { it >= 0 }
        val imageCount = info.progress.getInt(BackupWorker.KEY_IMAGE_COUNT, -1).takeIf { it >= 0 }
        return BackupWorkState(
            workId = info.id.toString(),
            jobId = info.tags.first { it.startsWith(JOB_TAG_PREFIX) }.removePrefix(JOB_TAG_PREFIX),
            operation = operation,
            state = info.state,
            progress = stage?.let {
                BackupJobProgress(
                    operation = operation,
                    stage = it,
                    completedBytes = info.progress.getLong(BackupWorker.KEY_COMPLETED_BYTES, 0L),
                    totalBytes = totalBytes,
                    completedItems = completedItems,
                    totalItems = totalItems,
                    sourceCount = sourceCount,
                    songCount = songCount,
                    imageCount = imageCount,
                )
            },
            error = info.outputData.getString(BackupWorker.KEY_ERROR),
            failureCode = info.outputData.getString(BackupWorker.KEY_FAILURE_CODE)
                ?.let { runCatching { BackupFailureCode.valueOf(it) }.getOrNull() },
            failureDetail = info.outputData.getString(BackupWorker.KEY_FAILURE_DETAIL),
            uri = (info.outputData.getString(BackupWorker.KEY_URI)
                ?: preferences.getString(META_URI_PREFIX + info.id, null))?.let(Uri::parse),
        )
    }

    private fun requestedAt(info: WorkInfo): Long =
        info.tags.firstOrNull { it.startsWith(REQUEST_TAG_PREFIX) }
            ?.removePrefix(REQUEST_TAG_PREFIX)?.toLongOrNull() ?: 0L

    private fun cleanupOldTemporaryFiles() {
        val cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
        context.cacheDir.listFiles()?.filter {
            it.name.startsWith("swan-restore-") && it.lastModified() < cutoff
        }?.forEach(File::delete)
    }

    private fun removePartialDocument(uri: Uri) {
        runCatching {
            if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                context.contentResolver.openOutputStream(uri, "wt")?.close()
            }
        }.onFailure {
            runCatching { context.contentResolver.openOutputStream(uri, "wt")?.close() }
        }
    }

    private companion object {
        const val OPERATION_TAG_PREFIX = "swan.backup.operation:"
        const val JOB_TAG_PREFIX = "swan.backup.job:"
        const val REQUEST_TAG_PREFIX = "swan.backup.requested:"
        const val PREFERENCES = "backup_jobs"
        const val KEY_ACKNOWLEDGED = "acknowledged"
        const val META_URI_PREFIX = "uri:"
        const val MAX_ACKNOWLEDGED = 20
        const val TEMP_FILE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
