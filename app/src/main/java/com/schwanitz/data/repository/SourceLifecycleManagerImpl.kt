package com.schwanitz.data.repository

import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.domain.repository.SourceLifecycleManager
import com.schwanitz.domain.repository.SourceManager
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceLifecycleManagerImpl @Inject constructor(
    private val songDao: SongDao,
    private val songLyricsDao: SongLyricsDao,
    private val sourceManager: SourceManager,
    private val sourceRegistry: MusicSourceRegistry,
    private val scanOrchestrator: ScanOrchestrator
) : SourceLifecycleManager {

    override suspend fun refreshSource(sourceId: String, onProgress: (Int, Int) -> Unit) {
        Timber.d("refreshSource started for %s", sourceId)
        val config = sourceManager.getSourceById(sourceId) ?: run {
            Timber.e("Config not found for %s", sourceId)
            return
        }
        val source = sourceRegistry.get(config.type) ?: run {
            Timber.e("Source registry returned null for %s", config.type)
            return
        }
        Timber.d("Loading songs from source...")
        val result = try {
            source.loadSongs(config, onProgress)
        } catch (e: Exception) {
            Timber.e(e, "Error loading songs for %s", sourceId)
            throw e
        }
        Timber.d("Found %d songs, %d albums. Replacing old data...", result.songs.size, result.albums.size)
        songLyricsDao.deleteBySource(sourceId)
        songDao.deleteBySource(sourceId)
        scanOrchestrator.deleteOrphanedAlbums()
        scanOrchestrator.persistScanResult(result)
        scanOrchestrator.cleanupOrphanedArtworkFiles()
        scanOrchestrator.cleanupOrphanedArtists()
        scanOrchestrator.refreshAlbumSeries()
        Timber.i("refreshSource finished for %s: %d songs, %d albums", sourceId, result.songs.size, result.albums.size)
    }

    override suspend fun deleteBySource(sourceId: String) {
        Timber.d("Deleting all data for source %s", sourceId)
        songLyricsDao.deleteBySource(sourceId)
        songDao.deleteBySource(sourceId)
        scanOrchestrator.deleteOrphanedAlbums()
        scanOrchestrator.cleanupOrphanedArtworkFiles()
        scanOrchestrator.cleanupOrphanedArtists()
        scanOrchestrator.refreshAlbumSeries()
    }

    override suspend fun setSourceActive(sourceId: String, active: Boolean) {
        songDao.setActiveBySource(sourceId, active)
    }

    override suspend fun reloadEnabled(onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit) {
        val enabledSources = sourceManager.getEnabledSources()
        Timber.i("Reloading %d enabled sources", enabledSources.size)
        for (config in enabledSources) {
            val source = sourceRegistry.get(config.type) ?: continue
            Timber.d("Reloading source: %s (%s)", config.name, config.type)
            try {
                songLyricsDao.deleteBySource(config.id)
                songDao.deleteBySource(config.id)
                scanOrchestrator.deleteOrphanedAlbums()
                val result = source.loadSongs(config) { scanned, total ->
                    onProgress(config.name, scanned, total)
                }
                scanOrchestrator.persistScanResult(result)
            } catch (e: Exception) {
                Timber.e(e, "Error reloading source %s, skipping", config.name)
            }
        }
        scanOrchestrator.cleanupOrphanedArtworkFiles()
        scanOrchestrator.cleanupOrphanedArtists()
        scanOrchestrator.refreshAlbumSeries()
    }
}
