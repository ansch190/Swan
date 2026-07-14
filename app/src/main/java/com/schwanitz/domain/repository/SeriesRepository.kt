package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface SeriesRepository {
    fun getAlbumSeries(): Flow<List<AlbumSeries>>
    fun getSeriesForAlbum(albumId: Long): Flow<AlbumSeries?>
    suspend fun getSeriesByName(name: String): AlbumSeries?
    fun getSongsBySeries(seriesId: Long): Flow<List<Song>>
    fun getAlbumsInSeries(seriesId: Long): Flow<List<Album>>
}
