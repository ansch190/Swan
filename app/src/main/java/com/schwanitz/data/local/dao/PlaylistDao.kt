package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongCrossRef
import com.schwanitz.data.local.entity.PlaylistWithCount
import com.schwanitz.data.local.entity.PlaylistWithSongs
import com.schwanitz.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = p.id) AS song_count FROM playlists p ORDER BY p.name ASC")
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    @Query("SELECT COUNT(*) FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN playlist_song_cross_ref pscr ON s.id = pscr.songId 
        WHERE pscr.playlistId = :playlistId 
        ORDER BY pscr.orderIndex ASC
    """)
    fun getPlaylistSongsOrdered(playlistId: Long): Flow<List<SongEntity>>

    @Query("UPDATE playlist_song_cross_ref SET orderIndex = :orderIndex WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateOrderIndex(playlistId: Long, songId: String, orderIndex: Int)

    @Transaction
    suspend fun reorderSongs(playlistId: Long, songIds: List<String>) {
        songIds.forEachIndexed { index, songId ->
            updateOrderIndex(playlistId, songId, index)
        }
    }
}
