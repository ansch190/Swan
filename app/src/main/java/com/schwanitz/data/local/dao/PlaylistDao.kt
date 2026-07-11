package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongMapping
import com.schwanitz.data.local.entity.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    data class PlaylistSongWithNames(
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
        SELECT s.id, s.title, s.artistId, asm.albumId,
            s.durationMs, s.sourceId, s.isFavorite, s.isActive,
            asm.discNumber, asm.trackNumber,
            al.year, s.genre, s.tagVersion,
            sti.mimeType, sti.sampleRate, sti.bitrate, sti.fileSize,
            a.name as artistName,
            al.name as albumName,
            aw.uriSmall as albumArtUri,
            aw.uriLarge as albumArtUriLarge,
            al.albumArtist as albumArtistName
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN artists a ON s.artistId = a.id
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        LEFT JOIN song_technical_info sti ON s.id = sti.songId
        INNER JOIN playlist_song_mapping pscr ON s.id = pscr.songId
        WHERE pscr.playlistId = :playlistId
        ORDER BY pscr.orderIndex ASC
    """)
    fun getPlaylistSongsOrdered(playlistId: Long): Flow<List<PlaylistSongWithNames>>

    @Query("UPDATE playlist_song_mapping SET orderIndex = :orderIndex WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateOrderIndex(playlistId: Long, songId: String, orderIndex: Int)

    @Transaction
    suspend fun reorderSongs(playlistId: Long, songIds: List<String>) {
        songIds.forEachIndexed { index, songId ->
            updateOrderIndex(playlistId, songId, index)
        }
    }
}
