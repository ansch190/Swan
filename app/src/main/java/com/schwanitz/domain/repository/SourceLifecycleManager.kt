package com.schwanitz.domain.repository

import com.schwanitz.domain.error.AppError

sealed interface SourceRefreshResult {
    data class Success(
        val total: Int,
        val updated: Int,
        val retainedFailures: Int
    ) : SourceRefreshResult

    data class Failure(val error: AppError) : SourceRefreshResult
}

interface SourceLifecycleManager {
    suspend fun refreshSource(sourceId: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): SourceRefreshResult
    suspend fun deleteBySource(sourceId: String)
    suspend fun setSourceActive(sourceId: String, active: Boolean)
    suspend fun reloadEnabled(
        onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit = { _, _, _ -> }
    ): Map<String, SourceRefreshResult>
}
