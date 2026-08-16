package com.schwanitz.data.backup

enum class RestoreStage {
    PREPARING_KEY,
    DECRYPTING,
    VALIDATING,
    EXTRACTING_ASSETS,
    RESTORING_LIBRARY,
    APPLYING_SETTINGS,
    FINALIZING
}

data class RestoreProgress(
    val stage: RestoreStage,
    val completedBytes: Long = 0,
    val totalBytes: Long? = null,
    val completedItems: Int? = null,
    val totalItems: Int? = null
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.let { (completedBytes.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

internal class RestoreProgressReporter(
    private val callback: (RestoreProgress) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var lastStage: RestoreStage? = null
    private var lastUpdateNanos = 0L

    fun report(progress: RestoreProgress, force: Boolean = false) {
        val now = nanoTime()
        val stageChanged = progress.stage != lastStage
        val complete = progress.totalBytes != null && progress.totalBytes > 0 &&
            progress.completedBytes >= progress.totalBytes
        if (force || stageChanged || complete || now - lastUpdateNanos >= UPDATE_INTERVAL_NANOS) {
            lastStage = progress.stage
            lastUpdateNanos = now
            callback(progress)
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_NANOS = 100_000_000L
    }
}
