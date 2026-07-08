package com.schwanitz.data.repository

import android.content.Context
import android.net.Uri
import com.schwanitz.data.local.dao.SongArtworkDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.data.local.converter.toEntity
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val songArtworkDao: SongArtworkDao,
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

    override fun getSongsByAlbum(album: String): Flow<List<Song>> {
        return songDao.getSongsByAlbum(album).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getSongsByArtist(artist: String): Flow<List<Song>> {
        return songDao.getSongsByArtist(artist).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByArtist(artist: String): Flow<List<com.schwanitz.domain.model.Album>> {
        return songDao.getAlbumsByArtist(artist).map { projections ->
            projections.map { com.schwanitz.domain.model.Album(it.album, it.albumArtUri) }
        }
    }

    override fun getSongsByYear(year: Int): Flow<List<Song>> {
        return songDao.getSongsByYear(year).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByYear(year: Int): Flow<List<com.schwanitz.domain.model.Album>> {
        return songDao.getAlbumsByYear(year).map { projections ->
            projections.map { com.schwanitz.domain.model.Album(it.album, it.albumArtUri) }
        }
    }

    override fun getSongsByGenre(genre: String): Flow<List<Song>> {
        return songDao.getSongsByGenre(genre).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByGenre(genre: String): Flow<List<com.schwanitz.domain.model.Album>> {
        return songDao.getAlbumsByGenre(genre).map { projections ->
            projections.map { com.schwanitz.domain.model.Album(it.album, it.albumArtUri) }
        }
    }

    override fun getArtistsByGenre(genre: String): Flow<List<String>> {
        return songDao.getArtistsByGenre(genre)
    }

    override suspend fun getSongById(songId: String): Song? {
        return songDao.getSongById(songId)?.toDomain()
    }

    override suspend fun getSongArtworks(songId: String): List<SongArtwork> {
        return songArtworkDao.getForSong(songId).map { it.toDomain() }
    }

    override suspend fun toggleFavorite(songId: String) {
        songDao.toggleFavorite(songId)
    }

    override suspend fun deleteBySource(sourceId: String) {
        songDao.deleteBySource(sourceId)
        songArtworkDao.deleteBySource(sourceId)
        cleanupOrphanedArtworkFiles()
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
        songDao.deleteBySource(sourceId)
        songArtworkDao.deleteBySource(sourceId)
        android.util.Log.d("MusicRepository", "Loading songs from source...")
        try {
            val result = source.loadSongs(config, onProgress)
            android.util.Log.d("MusicRepository", "Found ${result.songs.size} songs. Upserting...")
            songDao.upsertAll(result.songs.map { it.toEntity().copy(isActive = true) })
            songArtworkDao.upsertAll(result.artworks.map { it.toEntity() })
            android.util.Log.d("MusicRepository", "refreshSource finished for $sourceId")
        } catch (e: Exception) {
            android.util.Log.e("MusicRepository", "Error during refreshSource for $sourceId", e)
        }
        cleanupOrphanedArtworkFiles()
    }

    override suspend fun setSourceActive(sourceId: String, active: Boolean) {
        songDao.setActiveBySource(sourceId, active)
    }

    override suspend fun reloadEnabled(onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit) {
        val enabledSources = sourceManager.getEnabledSources()
        for (config in enabledSources) {
            val source = sourceRegistry.get(config.type) ?: continue
            songDao.deleteBySource(config.id)
            songArtworkDao.deleteBySource(config.id)
            val result = source.loadSongs(config) { scanned, total ->
                onProgress(config.name, scanned, total)
            }
            songDao.upsertAll(result.songs.map { it.toEntity().copy(isActive = true) })
            songArtworkDao.upsertAll(result.artworks.map { it.toEntity() })
        }
        cleanupOrphanedArtworkFiles()
    }

    private suspend fun cleanupOrphanedArtworkFiles() {
        val usedUris = (songDao.getAllAlbumArtUris() + songArtworkDao.getAllUris()).toSet()
        com.schwanitz.data.source.ArtworkCache.deleteUnused(context, usedUris)
    }
}
