package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongMapping
import com.schwanitz.data.local.entity.PlaylistWithCount
import com.schwanitz.data.local.entity.SongWithNames
import kotlinx.coroutines.flow.Flow

data class PlaylistSongWithMapping(
    val mappingId: Long,
    val id: String,
    val title: String,
    val artistId: Long?,
    val artistName: String?,
    val albumId: Long?,
    val albumName: String?,
    val albumArtistName: String?,
    val durationMs: Long,
    val albumArtUri: String?,
    val albumArtUriLarge: String?,
    val sourceId: String,
    val isFavorite: Boolean,
    val isActive: Boolean,
    val discNumber: Int,
    val trackNumber: Int,
    val year: Int,
    val genre: String,
    val mimeType: String,
    val sampleRate: Int,
    val bitrate: Int,
    val fileSize: Long,
    val tagVersion: String
)

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

    @Insert
    suspend fun addSongToPlaylist(crossRef: PlaylistSongMapping)

    @Query("DELETE FROM playlist_song_mapping WHERE id = :mappingId")
    suspend fun deleteByMappingId(mappingId: Long)

    @Query("DELETE FROM playlist_song_mapping WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeAllBySongId(playlistId: Long, songId: String)

    @Query("SELECT id FROM playlist_song_mapping WHERE playlistId = :playlistId AND songId = :songId ORDER BY orderIndex ASC LIMIT 1")
    suspend fun getFirstMappingId(playlistId: Long, songId: String): Long?

    @Query("SELECT COUNT(*) FROM playlist_song_mapping WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun getSongCountInPlaylist(playlistId: Long, songId: String): Int

    @Query("""
        SELECT songView.* FROM SongWithNames songView
        INNER JOIN playlist_song_mapping pscr ON songView.id = pscr.songId
        WHERE pscr.playlistId = :playlistId
        ORDER BY pscr.orderIndex ASC
    """)
    fun getPlaylistSongsOrdered(playlistId: Long): Flow<List<SongWithNames>>

    @Query("""
        SELECT pscr.id as mappingId, songView.* FROM SongWithNames songView
        INNER JOIN playlist_song_mapping pscr ON songView.id = pscr.songId
        WHERE pscr.playlistId = :playlistId
        ORDER BY pscr.orderIndex ASC
    """)
    fun getPlaylistSongsOrderedWithMapping(playlistId: Long): Flow<List<PlaylistSongWithMapping>>

    @Query("UPDATE playlist_song_mapping SET orderIndex = :orderIndex WHERE id = :mappingId")
    suspend fun updateOrderIndex(mappingId: Long, orderIndex: Int)

    @Transaction
    suspend fun reorderSongs(mappingIds: List<Long>) {
        mappingIds.forEachIndexed { index, mappingId ->
            updateOrderIndex(mappingId, index)
        }
    }
}
