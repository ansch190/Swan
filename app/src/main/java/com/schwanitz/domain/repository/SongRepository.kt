package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun getFavoriteSongs(): Flow<List<Song>>
    suspend fun getSongById(songId: String): Song?
    fun getSongsByAlbumId(albumId: Long): Flow<List<Song>>
    fun getSongsByArtistId(artistId: Long): Flow<List<Song>>
    fun getAlbumsByArtistId(artistId: Long): Flow<List<Album>>
    fun getSongsByYear(year: Int): Flow<List<Song>>
    fun getAlbumsByYear(year: Int): Flow<List<Album>>
    fun getSongsByGenre(genre: String): Flow<List<Song>>
    fun getAlbumsByGenre(genre: String): Flow<List<Album>>
    fun getArtistsByGenre(genre: String): Flow<List<String>>
    fun getAllArtistNames(): Flow<List<String>>
    fun getAllAlbumArtistNames(): Flow<List<String>>
    fun getAlbumArtistsByGenre(genre: String): Flow<List<String>>
    fun getSongsByAlbumArtistName(albumArtistName: String): Flow<List<Song>>
    fun getAlbumsByAlbumArtistName(albumArtistName: String): Flow<List<Album>>
    fun getSongsWithNoAlbumArtist(): Flow<List<Song>>
    fun getAlbumsWithNoAlbumArtist(): Flow<List<Album>>
    fun hasAlbumsWithNoAlbumArtist(): Flow<Boolean>
    fun getAllAlbums(): Flow<List<Album>>
    fun getAllYears(): Flow<List<Int>>
    fun getAllGenres(): Flow<List<String>>
    fun getSongsWithNoArtist(): Flow<List<Song>>
    fun getAlbumsWithNoArtist(): Flow<List<Album>>
    fun hasSongsWithNoArtist(): Flow<Boolean>
    suspend fun toggleFavorite(songId: String)
}
