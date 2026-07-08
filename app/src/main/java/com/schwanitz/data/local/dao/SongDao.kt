package com.schwanitz.data.local.dao

import androidx.room.*
import com.schwanitz.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE isActive = 1 ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isActive = 1 AND isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isActive = 1 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%')")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album AND isActive = 1 ORDER BY discNumber ASC, trackNumber ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist AND isActive = 1 ORDER BY album ASC, discNumber ASC, trackNumber ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT album, MAX(albumArtUri) as albumArtUri FROM songs WHERE albumArtist = :artist AND isActive = 1 GROUP BY album ORDER BY album ASC")
    fun getAlbumsByArtist(artist: String): Flow<List<AlbumProjection>>

    @Query("SELECT * FROM songs WHERE year = :year AND isActive = 1 ORDER BY album ASC, discNumber ASC, trackNumber ASC")
    fun getSongsByYear(year: Int): Flow<List<SongEntity>>

    @Query("SELECT album, MAX(albumArtUri) as albumArtUri FROM songs WHERE year = :year AND isActive = 1 GROUP BY album ORDER BY album ASC")
    fun getAlbumsByYear(year: Int): Flow<List<AlbumProjection>>

    @Query("SELECT * FROM songs WHERE genre = :genre AND isActive = 1 ORDER BY album ASC, discNumber ASC, trackNumber ASC")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT album, MAX(albumArtUri) as albumArtUri FROM songs WHERE genre = :genre AND isActive = 1 GROUP BY album ORDER BY album ASC")
    fun getAlbumsByGenre(genre: String): Flow<List<AlbumProjection>>

    @Query("SELECT DISTINCT artist FROM songs WHERE genre = :genre AND isActive = 1 ORDER BY artist ASC")
    fun getArtistsByGenre(genre: String): Flow<List<String>>

    data class AlbumProjection(
        val album: String,
        val albumArtUri: String?
    )

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): SongEntity?

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("UPDATE songs SET isFavorite = NOT isFavorite WHERE id = :songId")
    suspend fun toggleFavorite(songId: String)

    @Query("DELETE FROM songs WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("SELECT albumArtUri FROM songs WHERE albumArtUri IS NOT NULL")
    suspend fun getAllAlbumArtUris(): List<String>

    @Query("UPDATE songs SET isActive = :active WHERE sourceId = :sourceId")
    suspend fun setActiveBySource(sourceId: String, active: Boolean)
}
