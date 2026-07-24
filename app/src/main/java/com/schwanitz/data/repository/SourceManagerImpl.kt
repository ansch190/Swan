package com.schwanitz.data.repository

import com.schwanitz.data.local.CredentialStore
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.data.local.converter.toEntity
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceManagerImpl @Inject constructor(
    private val sourceConfigDao: SourceConfigDao,
    private val credentialStore: CredentialStore
) : SourceManager {

    override val sources: Flow<List<SourceConfig>> =
        sourceConfigDao.getAll().map { entities ->
            entities.map { it.toDomain().withCredentials() }
        }

    override suspend fun addSource(config: SourceConfig) {
        Timber.i("Adding source: %s (%s)", config.name, config.type)
        sourceConfigDao.upsert(config.toEntity())
        config.saveCredentials()
    }

    override suspend fun updateSource(config: SourceConfig) {
        Timber.i("Updating source: %s (%s)", config.name, config.type)
        sourceConfigDao.upsert(config.toEntity())
        config.saveCredentials()
    }

    override suspend fun removeSource(sourceId: String) {
        val entity = sourceConfigDao.getById(sourceId) ?: return
        Timber.i("Removing source: %s", entity.name)
        sourceConfigDao.delete(entity)
        credentialStore.delete(sourceId)
    }

    override suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        Timber.i("Source %s %s", sourceId, if (enabled) "enabled" else "disabled")
        sourceConfigDao.setEnabled(sourceId, enabled)
    }

    override suspend fun getEnabledSources(): List<SourceConfig> {
        return sourceConfigDao.getEnabled().map { it.toDomain().withCredentials() }
    }

    override suspend fun getSourceById(id: String): SourceConfig? {
        return sourceConfigDao.getById(id)?.toDomain()?.withCredentials()
    }

    private fun SourceConfig.saveCredentials() {
        if (type == com.schwanitz.domain.source.SourceType.WEBDAV || type == com.schwanitz.domain.source.SourceType.SMB) {
            val u = username
            val p = password
            if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
                credentialStore.save(id, u, p)
            }
        }
    }

    private fun SourceConfig.withCredentials(): SourceConfig {
        if (type != com.schwanitz.domain.source.SourceType.WEBDAV && type != com.schwanitz.domain.source.SourceType.SMB) return this
        val (u, p) = credentialStore.load(id) ?: return this
        return copy(username = u, password = p)
    }
}
