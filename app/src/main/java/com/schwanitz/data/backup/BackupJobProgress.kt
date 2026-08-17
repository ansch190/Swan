package com.schwanitz.data.backup

enum class BackupOperation { EXPORT, RESTORE }

enum class BackupJobStage {
    PREPARING,
    PREPARING_KEY,
    WRITING_CONFIGURATION,
    EXPORTING_LIBRARY,
    EXPORTING_ASSETS,
    FINALIZING,
    DECRYPTING,
    VALIDATING,
    EXTRACTING_ASSETS,
    RESTORING_LIBRARY,
    APPLYING_SETTINGS,
    WAITING_FOR_SCANS
}

data class BackupJobProgress(
    val operation: BackupOperation,
    val stage: BackupJobStage,
    val completedBytes: Long = 0,
    val totalBytes: Long? = null,
    val completedItems: Int? = null,
    val totalItems: Int? = null,
) {
    val fraction: Float?
        get() = when {
            totalBytes != null && totalBytes > 0 -> completedBytes.toFloat().div(totalBytes).coerceIn(0f, 1f)
            completedItems != null && totalItems != null && totalItems > 0 ->
                completedItems.toFloat().div(totalItems).coerceIn(0f, 1f)
            else -> null
        }
}

internal fun RestoreProgress.asJobProgress() = BackupJobProgress(
    operation = BackupOperation.RESTORE,
    stage = when (stage) {
        RestoreStage.PREPARING_KEY -> BackupJobStage.PREPARING_KEY
        RestoreStage.DECRYPTING -> BackupJobStage.DECRYPTING
        RestoreStage.VALIDATING -> BackupJobStage.VALIDATING
        RestoreStage.EXTRACTING_ASSETS -> BackupJobStage.EXTRACTING_ASSETS
        RestoreStage.RESTORING_LIBRARY -> BackupJobStage.RESTORING_LIBRARY
        RestoreStage.APPLYING_SETTINGS -> BackupJobStage.APPLYING_SETTINGS
        RestoreStage.FINALIZING -> BackupJobStage.FINALIZING
    },
    completedBytes = completedBytes,
    totalBytes = totalBytes,
    completedItems = completedItems,
    totalItems = totalItems,
)

internal class BackupProgressReporter(
    private val callback: (BackupJobProgress) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var lastStage: BackupJobStage? = null
    private var lastUpdateNanos = 0L
    private var lastProgress: BackupJobProgress? = null

    fun report(progress: BackupJobProgress, force: Boolean = false) {
        val previous = lastProgress
        val normalized = if (previous?.stage == progress.stage) {
            progress.copy(
                completedBytes = maxOf(previous.completedBytes, progress.completedBytes),
                completedItems = when {
                    previous.completedItems == null -> progress.completedItems
                    progress.completedItems == null -> previous.completedItems
                    else -> maxOf(previous.completedItems, progress.completedItems)
                },
            )
        } else progress
        val now = nanoTime()
        val stageChanged = normalized.stage != lastStage
        val complete = normalized.fraction?.let { it >= 1f } == true
        if (force || stageChanged || complete || now - lastUpdateNanos >= UPDATE_INTERVAL_NANOS) {
            lastStage = normalized.stage
            lastUpdateNanos = now
            lastProgress = normalized
            callback(normalized)
        }
    }

    private companion object {
        const val UPDATE_INTERVAL_NANOS = 150_000_000L
    }
}
