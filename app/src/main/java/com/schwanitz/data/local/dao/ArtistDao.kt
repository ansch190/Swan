package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schwanitz.data.local.entity.ArtistEntity

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artists WHERE name LIKE :name LIMIT 1")
    suspend fun findByName(name: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getById(id: Long): ArtistEntity?

    @Query("SELECT * FROM artists ORDER BY name ASC")
    suspend fun getAll(): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(artist: ArtistEntity): Long

    @Query("""
        DELETE FROM artists WHERE id NOT IN (
            SELECT DISTINCT artistId FROM songs WHERE artistId IS NOT NULL
            UNION
            SELECT DISTINCT a.id FROM artists a
            INNER JOIN albums al ON al.albumArtist = a.name
            INNER JOIN album_song_mapping asm ON al.id = asm.albumId
            INNER JOIN songs s ON s.id = asm.songId AND s.isActive = 1
        )
    """)
    suspend fun deleteOrphaned()
}
