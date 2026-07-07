package com.schwanitz.domain.repository

import com.schwanitz.domain.source.SourceConfig
import kotlinx.coroutines.flow.Flow

interface SourceManager {
    val sources: Flow<List<SourceConfig>>
    suspend fun addSource(config: SourceConfig)
    suspend fun updateSource(config: SourceConfig)
    suspend fun removeSource(sourceId: String)
    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean)
    suspend fun getEnabledSources(): List<SourceConfig>
    suspend fun getSourceById(id: String): SourceConfig?
}
