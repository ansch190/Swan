package com.schwanitz.data.repository

import android.content.Context
import android.net.Uri
import com.schwanitz.data.local.dao.AlbumArtworkDao
import com.schwanitz.data.local.dao.AlbumDao
import com.schwanitz.data.local.dao.AlbumSeriesDao
import com.schwanitz.data.local.dao.AlbumSongDao
import com.schwanitz.data.local.dao.ArtistDao
import com.schwanitz.data.local.dao.ArtistPicDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.dao.SongTechnicalInfoDao
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.data.local.converter.toEntity
import com.schwanitz.data.local.converter.toMappingEntity
import com.schwanitz.data.local.converter.toTechnicalInfoEntity
import com.schwanitz.data.local.entity.ArtistEntity
import com.schwanitz.data.source.ArtistImageCache
import com.schwanitz.data.source.SeriesDetector
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.SourceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val albumArtworkDao: AlbumArtworkDao,
    private val songLyricsDao: SongLyricsDao,
    private val songTechnicalInfoDao: SongTechnicalInfoDao,
    private val albumSongDao: AlbumSongDao,
    private val artistDao: ArtistDao,
    private val artistPicDao: ArtistPicDao,
    private val albumSeriesDao: AlbumSeriesDao,
    private val sourceManager: SourceManager,
    private val sourceRegistry: MusicSourceRegistry,
    @ApplicationContext private val context: Context
) : MusicRepository {

    override fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavoriteSongs(): Flow<List<Song>> {
        return songDao.getFavoriteSongs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSongsByAlbumId(albumId: Long): Flow<List<Song>> {
        return songDao.getSongsByAlbumId(albumId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSongsByArtistId(artistId: Long): Flow<List<Song>> {
        return songDao.getSongsByArtistId(artistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByArtistId(artistId: Long): Flow<List<Album>> {
        return songDao.getAlbumsByArtistId(artistId).map { projections ->
            projections.map { Album(id = it.albumId, name = it.albumName, albumArtist = it.albumArtist ?: "", albumArtUri = it.albumArtUri) }
        }
    }

    override fun getSongsByYear(year: Int): Flow<List<Song>> {
        return songDao.getSongsByYear(year).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByYear(year: Int): Flow<List<Album>> {
        return songDao.getAlbumsByYear(year).map { projections ->
            projections.map { Album(id = it.albumId, name = it.albumName, albumArtist = it.albumArtist ?: "", albumArtUri = it.albumArtUri) }
        }
    }

    override fun getSongsByGenre(genre: String): Flow<List<Song>> {
        return songDao.getSongsByGenre(genre).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByGenre(genre: String): Flow<List<Album>> {
        return songDao.getAlbumsByGenre(genre).map { projections ->
            projections.map { Album(id = it.albumId, name = it.albumName, albumArtist = it.albumArtist ?: "", albumArtUri = it.albumArtUri) }
        }
    }

    override fun getArtistsByGenre(genre: String): Flow<List<String>> {
        return songDao.getArtistsByGenre(genre)
    }

    override fun getAllArtistNames(): Flow<List<String>> {
        return songDao.getAllArtistNamesFlow()
    }

    override fun getAllAlbums(): Flow<List<Album>> {
        return songDao.getAllAlbums().map { projections ->
            projections.map { Album(id = it.albumId, name = it.albumName, albumArtist = it.albumArtist ?: "", albumArtUri = it.albumArtUri) }
        }
    }

    override fun getAllYears(): Flow<List<Int>> {
        return songDao.getAllYears()
    }

    override fun getAllGenres(): Flow<List<String>> {
        return songDao.getAllGenres()
    }

    override suspend fun getSongById(songId: String): Song? {
        return songDao.getSongById(songId)?.toDomain()
    }

    override suspend fun getAlbumArtworks(albumId: Long): List<AlbumArtwork> {
        return albumArtworkDao.getForAlbum(albumId).map { it.toDomain() }
    }

    override suspend fun toggleFavorite(songId: String) {
        songDao.toggleFavorite(songId)
    }

    override suspend fun setSourceActive(sourceId: String, active: Boolean) {
        songDao.setActiveBySource(sourceId, active)
    }

    override suspend fun reloadEnabled(onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit) {
        val enabledSources = sourceManager.getEnabledSources()
        for (config in enabledSources) {
            val source = sourceRegistry.get(config.type) ?: continue
            songLyricsDao.deleteBySource(config.id)
            songDao.deleteBySource(config.id)
            albumDao.deleteOrphaned()
            val result = source.loadSongs(config) { scanned, total ->
                onProgress(config.name, scanned, total)
            }
            processScanResult(result)
        }
        cleanupOrphanedArtworkFiles()
        cleanupOrphanedArtists()
        refreshAlbumSeries()
    }

    override fun getAlbumSeries(): Flow<List<AlbumSeries>> {
        return albumSeriesDao.getAllSeries().map { entities ->
            entities.map { AlbumSeries(it.id, it.name) }
        }
    }

    override fun getSeriesForAlbum(albumId: Long): Flow<AlbumSeries?> {
        return albumSeriesDao.getSeriesByAlbumId(albumId).map { entity ->
            entity?.let { AlbumSeries(it.id, it.name) }
        }
    }

    override suspend fun getSeriesByName(name: String): AlbumSeries? {
        return albumSeriesDao.getSeriesByName(name)?.let { AlbumSeries(it.id, it.name) }
    }

    override fun getSongsBySeries(seriesId: Long): Flow<List<Song>> {
        return songDao.getSongsBySeries(seriesId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsInSeries(seriesId: Long): Flow<List<Album>> {
        return songDao.getAlbumsInSeries(seriesId).map { projections ->
            projections.map { Album(id = it.albumId, name = it.albumName, albumArtist = it.albumArtist ?: "", albumArtUri = it.albumArtUri) }
        }
    }

    override suspend fun getTrackTotal(albumId: Long, discNumber: Int): Int {
        return albumSongDao.getTrackTotal(albumId, discNumber)
    }

    override suspend fun getDiscTotal(albumId: Long): Int {
        return albumSongDao.getDiscTotal(albumId)
    }

    override suspend fun refreshSource(sourceId: String, onProgress: (Int, Int) -> Unit) {
        android.util.Log.d("MusicRepository", "refreshSource started for $sourceId")
        val config = sourceManager.getSourceById(sourceId) ?: run {
            android.util.Log.e("MusicRepository", "Config not found for $sourceId")
            return
        }
        val source = sourceRegistry.get(config.type) ?: run {
            android.util.Log.e("MusicRepository", "Source registry returned null for ${config.type}")
            return
        }
        android.util.Log.d("MusicRepository", "Deleting old data for $sourceId")
        songLyricsDao.deleteBySource(sourceId)
        songDao.deleteBySource(sourceId)
        albumDao.deleteOrphaned()
        android.util.Log.d("MusicRepository", "Loading songs from source...")
        try {
            val result = source.loadSongs(config, onProgress)
            android.util.Log.d("MusicRepository", "Found ${result.songs.size} songs, ${result.albums.size} albums. Upserting...")
            processScanResult(result)
            android.util.Log.d("MusicRepository", "refreshSource finished for $sourceId")
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Error during refreshSource for $sourceId", e)
        }
        cleanupOrphanedArtworkFiles()
        cleanupOrphanedArtists()
        refreshAlbumSeries()
    }

    override suspend fun deleteBySource(sourceId: String) {
        songLyricsDao.deleteBySource(sourceId)
        songDao.deleteBySource(sourceId)
        albumDao.deleteOrphaned()
        cleanupOrphanedArtworkFiles()
        cleanupOrphanedArtists()
        refreshAlbumSeries()
    }

    private suspend fun processScanResult(result: com.schwanitz.domain.source.LoadSongsResult) {
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

    private suspend fun refreshAlbumSeries() {
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

    private suspend fun cleanupOrphanedArtists() {
        artistDao.deleteOrphaned()
        val usedSmallUris = artistPicDao.getAllSmallUris().toSet()
        val usedLargeUris = artistPicDao.getAllLargeUris().toSet()
        val usedUris = usedSmallUris + usedLargeUris
        ArtistImageCache.deleteUris(context, usedUris)
    }

    private suspend fun cleanupOrphanedArtworkFiles() {
        val usedUris = (
            albumArtworkDao.getAllLargeUris() +
            albumArtworkDao.getAllSmallUris()
        ).toSet()
        com.schwanitz.data.source.ArtworkCache.deleteUnused(context, usedUris)
    }
}
