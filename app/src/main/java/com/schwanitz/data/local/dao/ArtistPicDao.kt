package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.ArtistPicEntity

@Dao
interface ArtistPicDao {

    @Query("SELECT * FROM artist_pics WHERE artistId = :artistId")
    suspend fun getByArtistId(artistId: Long): ArtistPicEntity?

    @Query("SELECT uriSmall FROM artist_pics WHERE uriSmall IS NOT NULL")
    suspend fun getAllSmallUris(): List<String>

    @Query("SELECT uriLarge FROM artist_pics WHERE uriLarge IS NOT NULL")
    suspend fun getAllLargeUris(): List<String>

    @Upsert
    suspend fun upsert(pic: ArtistPicEntity)

    @Query("DELETE FROM artist_pics WHERE artistId = :artistId")
    suspend fun deleteByArtistId(artistId: Long)
}
