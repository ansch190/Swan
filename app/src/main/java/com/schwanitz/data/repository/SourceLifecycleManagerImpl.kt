package com.schwanitz.data.repository

import com.schwanitz.domain.error.AppError
import com.schwanitz.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceLifecycleManagerImpl @Inject constructor(
    private val songRepository: SongRepository,
    private val songLyricsRepository: SongLyricsRepository,
    private val sourceManager: SourceManager,
    private val sourceRegistry: MusicSourceRegistry,
    private val scanOrchestrator: ScanOrchestrator,
    private val libraryOperationCoordinator: LibraryOperationCoordinator,
) : SourceLifecycleManager {

    private val requestMutex = kotlinx.coroutines.sync.Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<SourceRefreshResult>>()

    override suspend fun refreshSource(
        sourceId: String,
        onProgress: (Int, Int) -> Unit
    ): SourceRefreshResult {
        var leader = false
        val request = requestMutex.withLock {
            val existing = inFlight[sourceId]
            if (existing != null) {
                existing
            } else {
                leader = true
                val created = CompletableDeferred<SourceRefreshResult>()
                inFlight[sourceId] = created
                created
            }
        }
        if (!leader) return request.await()
        try {
            val result = libraryOperationCoordinator.withScan { performRefresh(sourceId, onProgress) }
            request.complete(result)
            return result
        } catch (cancellation: CancellationException) {
            request.cancel(cancellation)
            throw cancellation
        } catch (error: Throwable) {
            request.completeExceptionally(error)
            throw error
        } finally {
            requestMutex.withLock { if (inFlight[sourceId] === request) inFlight.remove(sourceId) }
        }
    }

    private suspend fun performRefresh(
        sourceId: String,
        onProgress: (Int, Int) -> Unit
    ): SourceRefreshResult {
        val config = sourceManager.getSourceById(sourceId)
            ?: return SourceRefreshResult.Failure(AppError.source(sourceId, "Source config not found"))
        val source = sourceRegistry.get(config.type)
            ?: return SourceRefreshResult.Failure(AppError.source(config.name, "Source type is not registered"))
        val sessionId = scanOrchestrator.beginScan(sourceId)
        return try {
            val summary = source.loadSongs(config, onProgress) { event ->
                scanOrchestrator.stageEvent(sessionId, event)
            }
            scanOrchestrator.commitScan(sessionId, sourceId, config.isEnabled, summary)
            Timber.i("Refresh finished for %s: %d/%d", config.name, summary.succeeded, summary.total)
            SourceRefreshResult.Success(summary.total, summary.succeeded, summary.failedIds.size)
        } catch (cancellation: CancellationException) {
            scanOrchestrator.abortScan(sessionId)
            throw cancellation
        } catch (error: Exception) {
            scanOrchestrator.abortScan(sessionId)
            Timber.e(error, "Refresh failed for %s; previous data retained", config.name)
            SourceRefreshResult.Failure(AppError.from(error, "Could not refresh ${config.name}"))
        }
    }

    override suspend fun deleteBySource(sourceId: String) = libraryOperationCoordinator.withScan {
        songLyricsRepository.deleteBySource(sourceId)
        songRepository.deleteBySource(sourceId)
        scanOrchestrator.deleteOrphanedAlbums()
        scanOrchestrator.cleanupOrphanedArtworkFiles()
        scanOrchestrator.cleanupOrphanedArtists()
        scanOrchestrator.refreshAlbumSeries()
    }

    override suspend fun setSourceActive(sourceId: String, active: Boolean) {
        songRepository.setActiveBySource(sourceId, active)
    }

    override suspend fun reloadEnabled(
        onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit
    ): Map<String, SourceRefreshResult> {
        val results = linkedMapOf<String, SourceRefreshResult>()
        for (config in sourceManager.getEnabledSources()) {
            results[config.id] = refreshSource(config.id) { scanned, total ->
                onProgress(config.name, scanned, total)
            }
        }
        return results
    }
}
