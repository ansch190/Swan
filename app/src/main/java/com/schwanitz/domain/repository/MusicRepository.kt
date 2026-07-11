package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun getFavoriteSongs(): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun getSongsByAlbumId(albumId: Long): Flow<List<Song>>
    fun getSongsByArtistId(artistId: Long): Flow<List<Song>>
    fun getAlbumsByArtistId(artistId: Long): Flow<List<Album>>
    fun getSongsByYear(year: Int): Flow<List<Song>>
    fun getAlbumsByYear(year: Int): Flow<List<Album>>
    fun getSongsByGenre(genre: String): Flow<List<Song>>
    fun getAlbumsByGenre(genre: String): Flow<List<Album>>
    fun getArtistsByGenre(genre: String): Flow<List<String>>
    suspend fun getSongById(songId: String): Song?
    suspend fun getAlbumArtworks(albumId: Long): List<AlbumArtwork>
    suspend fun toggleFavorite(songId: String)
    suspend fun deleteBySource(sourceId: String)
    suspend fun refreshSource(sourceId: String, onProgress: (Int, Int) -> Unit = { _, _ -> })
    suspend fun setSourceActive(sourceId: String, active: Boolean)
    suspend fun reloadEnabled(onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit = { _, _, _ -> })

    fun getAllArtistNames(): Flow<List<String>>
    fun getAllAlbums(): Flow<List<Album>>
    fun getAllYears(): Flow<List<Int>>
    fun getAllGenres(): Flow<List<String>>

    fun getAlbumSeries(): Flow<List<AlbumSeries>>
    fun getSeriesForAlbum(albumId: Long): Flow<AlbumSeries?>
    suspend fun getSeriesByName(name: String): AlbumSeries?
    fun getSongsBySeries(seriesId: Long): Flow<List<Song>>
    fun getAlbumsInSeries(seriesId: Long): Flow<List<Album>>
    suspend fun getTrackTotal(albumId: Long, discNumber: Int): Int
    suspend fun getDiscTotal(albumId: Long): Int
    suspend fun getAlbumIdByNameAndArtist(albumName: String, albumArtist: String): Long?
}
