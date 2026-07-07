package com.schwanitz.data.repository

import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.data.local.converter.toEntity
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceManagerImpl @Inject constructor(
    private val sourceConfigDao: SourceConfigDao
) : SourceManager {

    override val sources: Flow<List<SourceConfig>> =
        sourceConfigDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addSource(config: SourceConfig) {
        sourceConfigDao.upsert(config.toEntity())
    }

    override suspend fun updateSource(config: SourceConfig) {
        sourceConfigDao.upsert(config.toEntity())
    }

    override suspend fun removeSource(sourceId: String) {
        val entity = sourceConfigDao.getById(sourceId) ?: return
        sourceConfigDao.delete(entity)
    }

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        sourceConfigDao.setEnabled(sourceId, enabled)
    }

    override suspend fun getEnabledSources(): List<SourceConfig> {
        return sourceConfigDao.getEnabled().map { it.toDomain() }
    }

    override suspend fun getSourceById(id: String): SourceConfig? {
        return sourceConfigDao.getById(id)?.toDomain()
    }
}
