package com.schwanitz.domain.repository

interface SourceLifecycleManager {
    suspend fun refreshSource(sourceId: String, onProgress: (Int, Int) -> Unit = { _, _ -> })
    suspend fun deleteBySource(sourceId: String)
    suspend fun setSourceActive(sourceId: String, active: Boolean)
    suspend fun reloadEnabled(onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit = { _, _, _ -> })
}
