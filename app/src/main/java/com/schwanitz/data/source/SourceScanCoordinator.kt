package com.schwanitz.data.source

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SourceScanWorkState(
    val workId: String,
    val sourceId: String,
    val sourceName: String,
    val state: WorkInfo.State,
    val requestedAt: Long,
    val scanned: Int,
    val total: Int,
    val updated: Int,
    val retainedFailures: Int,
    val error: String?,
)

@Singleton
class SourceScanCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val sourceManager: SourceManager,
) {
    private val workManager = WorkManager.getInstance(context)
    private val _scanRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scanRequested: SharedFlow<Unit> = _scanRequested.asSharedFlow()

    val workStates: Flow<List<SourceScanWorkState>> =
        workManager.getWorkInfosByTagFlow(SourceScanWorker.TAG).map { workInfos ->
            workInfos.mapNotNull(::toState)
                .groupBy(SourceScanWorkState::sourceId)
                .mapNotNull { (_, states) -> states.maxByOrNull(SourceScanWorkState::requestedAt) }
        }

    suspend fun enqueue(sourceId: String) {
        sourceManager.getSourceById(sourceId)?.let(::enqueue)
    }

    suspend fun enqueueEnabled() {
        sourceManager.getEnabledSources().forEach(::enqueue)
    }

    fun cancel(sourceId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(sourceId))
    }

    private fun enqueue(config: SourceConfig) {
        val requestedAt = System.currentTimeMillis()
        val constraints = Constraints.Builder().apply {
            if (config.type != SourceType.LOCAL) setRequiredNetworkType(NetworkType.CONNECTED)
        }.build()
        val request = OneTimeWorkRequestBuilder<SourceScanWorker>()
            .setInputData(
                workDataOf(
                    SourceScanWorker.KEY_SOURCE_ID to config.id,
                    SourceScanWorker.KEY_SOURCE_NAME to config.name,
                    SourceScanWorker.KEY_REQUESTED_AT to requestedAt,
                )
            )
            .setConstraints(constraints)
            .addTag(SourceScanWorker.TAG)
            .addTag(SourceScanWorker.SOURCE_TAG_PREFIX + config.id)
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(config.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
        _scanRequested.tryEmit(Unit)
    }

    private fun toState(info: WorkInfo): SourceScanWorkState? {
        val output = info.outputData
        val progress = info.progress
        val sourceId = progress.getString(SourceScanWorker.KEY_SOURCE_ID)
            ?: output.getString(SourceScanWorker.KEY_SOURCE_ID)
            ?: info.tags.firstOrNull { it.startsWith(SourceScanWorker.SOURCE_TAG_PREFIX) }
                ?.removePrefix(SourceScanWorker.SOURCE_TAG_PREFIX)
            ?: return null
        return SourceScanWorkState(
            workId = info.id.toString(),
            sourceId = sourceId,
            sourceName = progress.getString(SourceScanWorker.KEY_SOURCE_NAME)
                ?: output.getString(SourceScanWorker.KEY_SOURCE_NAME).orEmpty(),
            state = info.state,
            requestedAt = progress.getLong(
                SourceScanWorker.KEY_REQUESTED_AT,
                output.getLong(SourceScanWorker.KEY_REQUESTED_AT, 0L),
            ),
            scanned = progress.getInt(SourceScanWorker.KEY_SCANNED, 0),
            total = if (info.state.isFinished) {
                output.getInt(SourceScanWorker.KEY_TOTAL, 0)
            } else {
                progress.getInt(SourceScanWorker.KEY_TOTAL, 0)
            },
            updated = output.getInt(SourceScanWorker.KEY_UPDATED, 0),
            retainedFailures = output.getInt(SourceScanWorker.KEY_RETAINED_FAILURES, 0),
            error = output.getString(SourceScanWorker.KEY_ERROR),
        )
    }

    private fun uniqueWorkName(sourceId: String) = "swan-source-scan:$sourceId"
}
