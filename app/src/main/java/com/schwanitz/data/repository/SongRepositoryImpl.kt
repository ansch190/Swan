package com.schwanitz.data.repository

import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepositoryImpl @Inject constructor(
    private val songDao: SongDao
) : SongRepository {

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

    override suspend fun getSongById(songId: String): Song? {
        return songDao.getSongById(songId)?.toDomain()
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
            projections.map { it.toDomain() }
        }
    }

    override fun getSongsByYear(year: Int): Flow<List<Song>> {
        return songDao.getSongsByYear(year).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByYear(year: Int): Flow<List<Album>> {
        return songDao.getAlbumsByYear(year).map { projections ->
            projections.map { it.toDomain() }
        }
    }

    override fun getSongsByGenre(genre: String): Flow<List<Song>> {
        return songDao.getSongsByGenre(genre).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsByGenre(genre: String): Flow<List<Album>> {
        return songDao.getAlbumsByGenre(genre).map { projections ->
            projections.map { it.toDomain() }
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
            projections.map { it.toDomain() }
        }
    }

    override fun getAllYears(): Flow<List<Int>> {
        return songDao.getAllYears()
    }

    override fun getAllGenres(): Flow<List<String>> {
        return songDao.getAllGenres()
    }

    override fun getSongsWithNoArtist(): Flow<List<Song>> {
        return songDao.getSongsWithNoArtist().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAlbumsWithNoArtist(): Flow<List<Album>> {
        return songDao.getAlbumsWithNoArtist().map { projections ->
            projections.map { it.toDomain() }
        }
    }

    override fun hasSongsWithNoArtist(): Flow<Boolean> {
        return songDao.hasSongsWithNoArtist()
    }

    override suspend fun toggleFavorite(songId: String) {
        songDao.toggleFavorite(songId)
    }
}
