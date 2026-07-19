package com.schwanitz.data.repository

import android.content.Context
import com.schwanitz.data.local.dao.AlbumArtworkDao
import com.schwanitz.data.local.dao.AlbumDao
import com.schwanitz.data.local.dao.AlbumSeriesDao
import com.schwanitz.data.local.dao.AlbumSongDao
import com.schwanitz.data.local.dao.ArtistDao
import com.schwanitz.data.local.dao.ArtistPicDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SongTechnicalInfoDao
import com.schwanitz.data.local.converter.toEntity
import com.schwanitz.data.local.converter.toMappingEntity
import com.schwanitz.data.local.converter.toTechnicalInfoEntity
import com.schwanitz.data.local.entity.ArtistEntity
import com.schwanitz.data.source.ArtistImageCache
import com.schwanitz.data.source.SeriesDetector
import com.schwanitz.domain.source.LoadSongsResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanOrchestrator @Inject constructor(
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
    suspend fun persistScanResult(result: LoadSongsResult) {
        val artistNameToId = mutableMapOf<String, Long>()

        suspend fun resolveArtistId(name: String): Long? {
            if (name.isBlank()) return null
            return artistNameToId.getOrPut(name) {
                val existing = artistDao.findByName(name)
                existing?.id ?: artistDao.upsert(ArtistEntity(name = name))
            }
        }

        val albumEntities = result.albums.map { it.toEntity() }
        val upsertedAlbums = mutableListOf<Pair<String, Long>>()
        val artworkKeyId = mutableMapOf<String, Long>()
        for (albumEntity in albumEntities) {
            val existing = albumDao.findByNameAndAlbumArtist(albumEntity.name, albumEntity.albumArtist)
            val albumId = if (existing != null) {
                albumDao.upsert(albumEntity.copy(id = existing.id))
                existing.id
            } else {
                albumDao.upsert(albumEntity)
            }
            upsertedAlbums.add("${albumEntity.name}|${albumEntity.albumArtist}" to albumId)
            artworkKeyId["${albumEntity.albumArtist}|${albumEntity.name}|${albumEntity.year}"] = albumId
        }

        val songEntities = mutableListOf<com.schwanitz.data.local.entity.SongEntity>()
        val mappingEntities = mutableListOf<com.schwanitz.data.local.entity.AlbumSongMappingEntity>()
        for (song in result.songs) {
            val artistId = resolveArtistId(song.artistName)
            val albumKey = "${song.albumName}|${song.albumArtistName}"
            val albumId = upsertedAlbums.firstOrNull { it.first == albumKey }?.second
            songEntities.add(
                song.toEntity().copy(
                    isActive = true,
                    artistId = artistId
                )
            )
            if (albumId != null) {
                mappingEntities.add(song.toMappingEntity(albumId))
            }
        }
        songDao.upsertAll(songEntities)
        albumSongDao.upsertAll(mappingEntities)

        val technicalInfoEntities = result.songs.map { it.toTechnicalInfoEntity() }
        songTechnicalInfoDao.upsertAll(technicalInfoEntities)

        val artworkEntities = result.artworks.flatMap { (albumKey, artworks) ->
            val realAlbumId = artworkKeyId[albumKey] ?: return@flatMap emptyList()
            artworks.map { it.copy(albumId = realAlbumId).toEntity() }
        }
        albumArtworkDao.upsertAll(artworkEntities)
    }

    suspend fun refreshAlbumSeries() {
        val activeAlbums = songDao.getAllActiveAlbums()
        val albumNameToId = activeAlbums.associate { it.albumName to it.albumId }
        val albumNames = activeAlbums.map { it.albumName }.toSet()
        val detected = SeriesDetector.detectSeries(albumNames)
        albumSeriesDao.replaceAllSeries(detected.map { result ->
            com.schwanitz.data.local.dao.SeriesInput(
                seriesName = result.seriesName,
                volumes = result.volumes.map { vol ->
                    com.schwanitz.data.local.dao.SeriesVolume(
                        albumId = albumNameToId[vol.albumName] ?: 0L,
                        volumeNumber = vol.volumeNumber
                    )
                }
            )
        })
    }

    suspend fun cleanupOrphanedArtists() {
        artistDao.deleteOrphaned()
        val usedSmallUris = artistPicDao.getAllSmallUris().toSet()
        val usedLargeUris = artistPicDao.getAllLargeUris().toSet()
        val usedUris = usedSmallUris + usedLargeUris
        ArtistImageCache.deleteUnreferenced(context, usedUris)
    }

    suspend fun cleanupOrphanedArtworkFiles() {
        val usedUris = (
            albumArtworkDao.getAllLargeUris() +
            albumArtworkDao.getAllSmallUris()
        ).toSet()
        com.schwanitz.data.source.ArtworkCache.deleteUnused(context, usedUris)
    }

    suspend fun deleteOrphanedAlbums() {
        albumDao.deleteOrphaned()
    }
}
