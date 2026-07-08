package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.ArtistProfileEntity

@Dao
interface ArtistProfileDao {

    @Query("SELECT * FROM artist_profiles WHERE artistName = :artistName")
    suspend fun get(artistName: String): ArtistProfileEntity?

    @Upsert
    suspend fun upsert(entity: ArtistProfileEntity)

    @Query("DELETE FROM artist_profiles WHERE artistName = :artistName")
    suspend fun delete(artistName: String)
}
