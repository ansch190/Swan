package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun getAllSongs(): Flow<List<Song>>
    fun getFavoriteSongs(): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    fun getSongsByAlbum(album: String): Flow<List<Song>>
    fun getSongsByArtist(artist: String): Flow<List<Song>>
    fun getAlbumsByArtist(artist: String): Flow<List<com.schwanitz.domain.model.Album>>
    suspend fun getSongById(songId: String): Song?
    suspend fun getSongArtworks(songId: String): List<SongArtwork>
    suspend fun toggleFavorite(songId: String)
    suspend fun deleteBySource(sourceId: String)
    suspend fun refreshSource(sourceId: String, onProgress: (Int, Int) -> Unit = { _, _ -> })
    suspend fun setSourceActive(sourceId: String, active: Boolean)
    suspend fun reloadEnabled(onProgress: (sourceName: String, scanned: Int, total: Int) -> Unit = { _, _, _ -> })
}
