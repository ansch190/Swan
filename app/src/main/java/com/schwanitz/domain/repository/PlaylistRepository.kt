package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Playlist
import com.schwanitz.domain.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getAllPlaylists(): Flow<List<Playlist>>
    fun getAllPlaylistSongCounts(): Flow<Map<Long, Int>>
    fun getPlaylistName(playlistId: Long): Flow<String?>
    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun getPlaylistSongCount(playlistId: Long): Int
    suspend fun addSongToPlaylist(playlistId: Long, songId: String, order: Int)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String)
    suspend fun reorderSongs(playlistId: Long, songIds: List<String>)
}
