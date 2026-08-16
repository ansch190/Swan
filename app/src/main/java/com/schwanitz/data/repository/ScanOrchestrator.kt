package com.schwanitz.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.schwanitz.data.local.AppDatabase
import com.schwanitz.data.local.dao.*
import com.schwanitz.data.local.entity.*
import com.schwanitz.data.source.ArtistImageCache
import com.schwanitz.data.source.ArtworkCache
import com.schwanitz.data.source.SeriesDetector
import com.schwanitz.domain.source.AlbumKey
import com.schwanitz.domain.source.ScanEvent
import com.schwanitz.domain.source.ScanSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanOrchestrator @Inject constructor(
    private val database: AppDatabase,
    private val scanDao: ScanDao,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val albumArtworkDao: AlbumArtworkDao,
    private val songTechnicalInfoDao: SongTechnicalInfoDao,
    private val albumSongDao: AlbumSongDao,
    private val artistDao: ArtistDao,
    private val artistPicDao: ArtistPicDao,
    private val albumSeriesDao: AlbumSeriesDao,
    @ApplicationContext private val context: Context
) {
    suspend fun beginScan(sourceId: String): String {
        scanDao.deleteSessionsForSource(sourceId)
        return UUID.randomUUID().toString().also { id ->
            scanDao.insertSession(ScanSessionEntity(id, sourceId, System.currentTimeMillis()))
        }
    }

    suspend fun stageEvent(sessionId: String, event: ScanEvent) {
        when (event) {
            is ScanEvent.Discovered -> scanDao.insertDiscovered(
                event.songIds.distinct().map { ScanDiscoveredEntity(sessionId, it) }
            )
            is ScanEvent.Parsed -> {
                scanDao.insertSongs(event.batch.songs.map { song ->
                    ScanSongEntity(
                        sessionId, song.id, song.title, song.artistName, song.albumName,
                        song.albumArtistName, song.durationMs, song.sourceId, song.discNumber,
                        song.trackNumber, song.year, song.genre, song.mimeType, song.sampleRate,
                        song.bitrate, song.fileSize, song.tagVersion
                    )
                })
                scanDao.insertArtworks(event.batch.artworks.flatMap { (key, artworks) ->
                    artworks.map { artwork ->
                        ScanArtworkEntity(
                            sessionId, key.name, key.albumArtist, key.year,
                            artwork.sortOrder, artwork.uriLarge, artwork.uriSmall
                        )
                    }
                })
            }
        }
    }

    suspend fun commitScan(sessionId: String, sourceId: String, sourceActive: Boolean, summary: ScanSummary) {
        database.withTransaction {
            val stagedSongs = scanDao.getSongs(sessionId)
            val discoveredIds = scanDao.getDiscoveredIds(sessionId).toSet()
            require(discoveredIds.size == summary.total) {
                "Incomplete scan enumeration: staged=${discoveredIds.size}, reported=${summary.total}"
            }
            require(stagedSongs.size == summary.succeeded) {
                "Incomplete scan data: staged=${stagedSongs.size}, reported=${summary.succeeded}"
            }

            val existing = songDao.getEntitiesBySource(sourceId).associateBy { it.id }
            val artistIds = mutableMapOf<String, Long>()
            suspend fun resolveArtistId(name: String): Long? {
                if (name.isBlank()) return null
                return artistIds.getOrPut(name) {
                    artistDao.findByName(name)?.id ?: artistDao.upsert(ArtistEntity(name = name))
                }
            }

            val albumIds = mutableMapOf<AlbumKey, Long>()
            for (song in stagedSongs) {
                val key = AlbumKey(song.albumName, song.albumArtist, song.year)
                if (key !in albumIds) {
                    val found = albumDao.findByIdentity(key.name, key.albumArtist, key.year)
                    albumIds[key] = found?.id ?: albumDao.upsert(
                        AlbumEntity(name = key.name, albumArtist = key.albumArtist, year = key.year)
                    )
                }
            }

            val successfulIds = stagedSongs.map { it.songId }
            successfulIds.chunked(SQL_BATCH_SIZE).forEach { albumSongDao.deleteBySongIds(it) }
            songDao.upsertAll(stagedSongs.map { song ->
                SongEntity(
                    id = song.songId,
                    title = song.title,
                    artistId = resolveArtistId(song.artistName),
                    durationMs = song.durationMs,
                    sourceId = sourceId,
                    isFavorite = existing[song.songId]?.isFavorite ?: false,
                    isActive = sourceActive,
                    genre = song.genre,
                    tagVersion = song.tagVersion
                )
            })
            albumSongDao.upsertAll(stagedSongs.mapNotNull { song ->
                albumIds[AlbumKey(song.albumName, song.albumArtist, song.year)]?.let { albumId ->
                    AlbumSongMappingEntity(song.songId, albumId, song.trackNumber, song.discNumber)
                }
            })
            songTechnicalInfoDao.upsertAll(stagedSongs.map { song ->
                SongTechnicalInfoEntity(song.songId, song.fileSize, song.bitrate, song.sampleRate, song.mimeType)
            })

            val artworkRows = scanDao.getArtworks(sessionId)
            val artworkAlbumIds = artworkRows.mapNotNull {
                albumIds[AlbumKey(it.albumName, it.albumArtist, it.year)]
            }.distinct()
            artworkAlbumIds.chunked(SQL_BATCH_SIZE).forEach { albumArtworkDao.deleteByAlbumIds(it) }
            albumArtworkDao.upsertAll(artworkRows.mapNotNull { artwork ->
                albumIds[AlbumKey(artwork.albumName, artwork.albumArtist, artwork.year)]?.let { albumId ->
                    AlbumArtworkEntity(albumId, artwork.sortOrder, artwork.uriLarge, artwork.uriSmall)
                }
            })

            val removedIds = existing.keys - discoveredIds
            removedIds.chunked(SQL_BATCH_SIZE).forEach { songDao.deleteByIds(it) }
            albumDao.deleteOrphaned()
            artistDao.deleteOrphaned()
            refreshAlbumSeriesInDatabase()
            scanDao.deleteSession(sessionId)
        }
        cleanupOrphanedArtworkFiles()
        cleanupOrphanedArtistFiles()
    }

    suspend fun abortScan(sessionId: String) = scanDao.deleteSession(sessionId)

    suspend fun refreshAlbumSeries() = database.withTransaction { refreshAlbumSeriesInDatabase() }

    private suspend fun refreshAlbumSeriesInDatabase() {
        val activeAlbums = songDao.getAllActiveAlbums()
        val albumIdsByName = activeAlbums.groupBy({ it.albumName }, { it.albumId })
        val detected = SeriesDetector.detectSeries(activeAlbums.map { it.albumName }.toSet())
        albumSeriesDao.replaceAllSeries(detected.map { result ->
            SeriesInput(result.seriesName, result.volumes.flatMap { volume ->
                albumIdsByName[volume.albumName].orEmpty().map { albumId ->
                    SeriesVolume(albumId, volume.volumeNumber)
                }
            })
        })
    }

    suspend fun cleanupOrphanedArtists() {
        artistDao.deleteOrphaned()
        cleanupOrphanedArtistFiles()
    }

    private suspend fun cleanupOrphanedArtistFiles() {
        ArtistImageCache.deleteUnreferenced(
            context,
            (artistPicDao.getAllSmallUris() + artistPicDao.getAllLargeUris()).toSet()
        )
    }

    suspend fun cleanupOrphanedArtworkFiles() {
        ArtworkCache.deleteUnused(
            context,
            (albumArtworkDao.getAllLargeUris() + albumArtworkDao.getAllSmallUris()).toSet()
        )
    }

    suspend fun deleteOrphanedAlbums() = albumDao.deleteOrphaned()

    private companion object { const val SQL_BATCH_SIZE = 500 }
}
