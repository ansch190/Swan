package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.ArtistImageEntity

@Dao
interface ArtistImageDao {

    @Query("SELECT * FROM artist_images WHERE artistName = :artistName")
    suspend fun get(artistName: String): ArtistImageEntity?

    @Upsert
    suspend fun upsert(entity: ArtistImageEntity)

    @Query("SELECT * FROM artist_images")
    suspend fun getAll(): List<ArtistImageEntity>

    @Query("DELETE FROM artist_images WHERE artistName = :artistName")
    suspend fun delete(artistName: String)

    @Query("DELETE FROM artist_images WHERE artistName NOT IN (SELECT artist FROM songs UNION SELECT albumArtist FROM songs WHERE albumArtist IS NOT NULL AND albumArtist != '')")
    suspend fun deleteOrphaned()
}
