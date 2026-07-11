package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    data class SongWithNames(
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

    data class AlbumProjection(
        val albumId: Long,
        val albumName: String,
        val albumArtist: String?,
        val albumArtUri: String?
    )

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
        WHERE s.isActive = 1
        ORDER BY s.title ASC
    """)
    fun getAllSongs(): Flow<List<SongWithNames>>

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
        WHERE s.isActive = 1 AND s.isFavorite = 1
        ORDER BY s.title ASC
    """)
    fun getFavoriteSongs(): Flow<List<SongWithNames>>

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
        WHERE s.isActive = 1 AND (
            s.title LIKE '%' || :query || '%'
            OR a.name LIKE '%' || :query || '%'
            OR al.name LIKE '%' || :query || '%'
        )
    """)
    fun searchSongs(query: String): Flow<List<SongWithNames>>

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
        WHERE asm.albumId = :albumId AND s.isActive = 1
        ORDER BY asm.discNumber ASC, asm.trackNumber ASC
    """)
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongWithNames>>

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
        WHERE s.artistId = :artistId AND s.isActive = 1
        ORDER BY asm.albumId ASC, asm.discNumber ASC, asm.trackNumber ASC
    """)
    fun getSongsByArtistId(artistId: Long): Flow<List<SongWithNames>>

    @Query("""
        SELECT asm.albumId as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        WHERE al.albumArtist IN (SELECT name FROM artists WHERE id = :artistId) AND s.isActive = 1
        GROUP BY asm.albumId
        ORDER BY al.name ASC
    """)
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumProjection>>

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
        WHERE al.year = :year AND s.isActive = 1
        ORDER BY asm.albumId ASC, asm.discNumber ASC, asm.trackNumber ASC
    """)
    fun getSongsByYear(year: Int): Flow<List<SongWithNames>>

    @Query("""
        SELECT asm.albumId as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        WHERE al.year = :year AND s.isActive = 1
        GROUP BY asm.albumId
        ORDER BY al.name ASC
    """)
    fun getAlbumsByYear(year: Int): Flow<List<AlbumProjection>>

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
        WHERE s.genre = :genre AND s.isActive = 1
        ORDER BY asm.albumId ASC, asm.discNumber ASC, asm.trackNumber ASC
    """)
    fun getSongsByGenre(genre: String): Flow<List<SongWithNames>>

    @Query("""
        SELECT asm.albumId as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        WHERE s.genre = :genre AND s.isActive = 1
        GROUP BY asm.albumId
        ORDER BY al.name ASC
    """)
    fun getAlbumsByGenre(genre: String): Flow<List<AlbumProjection>>

    @Query("""
        SELECT DISTINCT a.name FROM songs s
        INNER JOIN artists a ON s.artistId = a.id
        WHERE s.genre = :genre AND s.isActive = 1
        ORDER BY a.name ASC
    """)
    fun getArtistsByGenre(genre: String): Flow<List<String>>

    @Query("SELECT name FROM artists ORDER BY name ASC")
    suspend fun getAllArtistNames(): List<String>

    @Query("SELECT name FROM artists ORDER BY name ASC")
    fun getAllArtistNamesFlow(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT al.albumArtist FROM albums al
        INNER JOIN album_song_mapping asm ON al.id = asm.albumId
        INNER JOIN songs s ON s.id = asm.songId
        WHERE s.isActive = 1 AND al.albumArtist IS NOT NULL AND al.albumArtist != ''
        ORDER BY al.albumArtist ASC
    """)
    suspend fun getAllAlbumArtistNames(): List<String>

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
        WHERE s.id = :id
    """)
    suspend fun getSongById(id: String): SongWithNames?

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = NOT isFavorite WHERE id = :songId")
    suspend fun toggleFavorite(songId: String)

    @Query("DELETE FROM songs WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("UPDATE songs SET isActive = :active WHERE sourceId = :sourceId")
    suspend fun setActiveBySource(sourceId: String, active: Boolean)

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
        INNER JOIN album_series_mapping asrm ON asm.albumId = asrm.albumId
        WHERE asrm.seriesId = :seriesId AND s.isActive = 1
        ORDER BY asrm.volumeNumber ASC, asm.discNumber ASC, asm.trackNumber ASC
    """)
    fun getSongsBySeries(seriesId: Long): Flow<List<SongWithNames>>

    @Query("""
        SELECT asm.albumId as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        INNER JOIN album_series_mapping asrm ON asm.albumId = asrm.albumId
        WHERE asrm.seriesId = :seriesId AND s.isActive = 1
        GROUP BY asm.albumId
        ORDER BY asrm.volumeNumber ASC
    """)
    fun getAlbumsInSeries(seriesId: Long): Flow<List<AlbumProjection>>

    @Query("""
        SELECT asm.albumId as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        WHERE s.isActive = 1
        GROUP BY asm.albumId
        ORDER BY al.name ASC
    """)
    fun getAllAlbums(): Flow<List<AlbumProjection>>

    @Query("""
        SELECT DISTINCT al.year FROM albums al
        INNER JOIN album_song_mapping asm ON al.id = asm.albumId
        INNER JOIN songs s ON s.id = asm.songId
        WHERE s.isActive = 1 AND al.year > 0
        ORDER BY al.year DESC
    """)
    fun getAllYears(): Flow<List<Int>>

    @Query("SELECT DISTINCT genre FROM songs WHERE isActive = 1 AND genre != '' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT al.id as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM albums al
        INNER JOIN album_song_mapping asm ON al.id = asm.albumId
        INNER JOIN songs s ON s.id = asm.songId
        LEFT JOIN album_artwork aw ON al.id = aw.albumId AND aw.sortOrder = 0
        WHERE s.isActive = 1
    """)
    suspend fun getAllActiveAlbums(): List<AlbumProjection>

    @Query("SELECT songId FROM album_song_mapping WHERE albumId = :albumId")
    suspend fun getSongIdsByAlbumId(albumId: Long): List<String>
}
