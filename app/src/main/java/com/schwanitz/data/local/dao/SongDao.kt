package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.SongEntity
import com.schwanitz.data.local.entity.SongWithNames
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    data class AlbumProjection(
        val albumId: Long,
        val albumName: String,
        val albumArtist: String?,
        val albumArtUri: String?
    )

    @Query("SELECT * FROM SongWithNames WHERE isActive = 1 ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongWithNames>>

    @Query("SELECT * FROM SongWithNames WHERE isActive = 1 AND isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongWithNames>>

    @Query("SELECT * FROM SongWithNames WHERE albumId = :albumId AND isActive = 1 ORDER BY discNumber ASC, trackNumber ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongWithNames>>

    @Query("SELECT * FROM SongWithNames WHERE artistId = :artistId AND isActive = 1 ORDER BY albumId ASC, discNumber ASC, trackNumber ASC")
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

    @Query("SELECT * FROM SongWithNames WHERE year = :year AND isActive = 1 ORDER BY albumId ASC, discNumber ASC, trackNumber ASC")
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

    @Query("SELECT * FROM SongWithNames WHERE genre = :genre AND isActive = 1 ORDER BY albumId ASC, discNumber ASC, trackNumber ASC")
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
    fun getAllArtistNamesFlow(): Flow<List<String>>

    @Query("SELECT * FROM SongWithNames WHERE id = :id")
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
        SELECT songView.* FROM SongWithNames songView
        INNER JOIN album_series_mapping asrm ON songView.albumId = asrm.albumId
        WHERE asrm.seriesId = :seriesId AND songView.isActive = 1
        ORDER BY asrm.volumeNumber ASC, songView.discNumber ASC, songView.trackNumber ASC
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

    @Query("SELECT * FROM SongWithNames WHERE artistId IS NULL AND isActive = 1 ORDER BY albumId ASC, discNumber ASC, trackNumber ASC")
    fun getSongsWithNoArtist(): Flow<List<SongWithNames>>

    @Query("""
        SELECT asm.albumId as albumId, al.name as albumName, al.albumArtist as albumArtist, aw.uriSmall as albumArtUri
        FROM songs s
        INNER JOIN album_song_mapping asm ON s.id = asm.songId
        LEFT JOIN albums al ON asm.albumId = al.id
        LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
        WHERE s.artistId IS NULL AND s.isActive = 1
        GROUP BY asm.albumId
        ORDER BY al.name ASC
    """)
    fun getAlbumsWithNoArtist(): Flow<List<AlbumProjection>>

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE artistId IS NULL AND isActive = 1)")
    fun hasSongsWithNoArtist(): Flow<Boolean>
}
