package com.schwanitz.data.repository

import com.schwanitz.data.local.dao.AlbumSeriesDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SeriesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val albumSeriesDao: AlbumSeriesDao,
    private val songDao: SongDao
) : SeriesRepository {

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
            projections.map { it.toDomain() }
        }
    }
}
