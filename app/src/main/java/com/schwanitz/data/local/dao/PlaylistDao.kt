package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongMapping
import com.schwanitz.data.local.entity.PlaylistWithCount
import com.schwanitz.data.local.entity.SongWithNames
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Transaction
    @Query("SELECT p.*, (SELECT COUNT(*) FROM playlist_song_mapping WHERE playlistId = p.id) AS song_count FROM playlists p ORDER BY p.name ASC")
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT name FROM playlists WHERE id = :playlistId")
    fun getPlaylistName(playlistId: Long): Flow<String?>

    @Insert
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    @Query("SELECT COUNT(*) FROM playlist_song_mapping WHERE playlistId = :playlistId")
    suspend fun getPlaylistSongCount(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongMapping)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongMapping)

    @Query("""
        SELECT songView.* FROM SongWithNames songView
        INNER JOIN playlist_song_mapping pscr ON songView.id = pscr.songId
        WHERE pscr.playlistId = :playlistId
        ORDER BY pscr.orderIndex ASC
    """)
    fun getPlaylistSongsOrdered(playlistId: Long): Flow<List<SongWithNames>>

    @Query("UPDATE playlist_song_mapping SET orderIndex = :orderIndex WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateOrderIndex(playlistId: Long, songId: String, orderIndex: Int)

    @Transaction
    suspend fun reorderSongs(playlistId: Long, songIds: List<String>) {
        songIds.forEachIndexed { index, songId ->
            updateOrderIndex(playlistId, songId, index)
        }
    }
}
