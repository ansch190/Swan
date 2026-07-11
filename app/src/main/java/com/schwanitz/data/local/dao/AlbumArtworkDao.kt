package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.AlbumArtworkEntity

@Dao
interface AlbumArtworkDao {

    @Query("SELECT * FROM album_artwork WHERE albumId = :albumId ORDER BY sortOrder")
    suspend fun getForAlbum(albumId: Long): List<AlbumArtworkEntity>

    @Query("SELECT * FROM album_artwork WHERE albumId = :albumId ORDER BY sortOrder")
    fun observeForAlbum(albumId: Long): kotlinx.coroutines.flow.Flow<List<AlbumArtworkEntity>>

    @Upsert
    suspend fun upsertAll(artworks: List<AlbumArtworkEntity>)

    @Query("SELECT uriLarge FROM album_artwork")
    suspend fun getAllLargeUris(): List<String>

    @Query("SELECT uriSmall FROM album_artwork WHERE uriSmall IS NOT NULL")
    suspend fun getAllSmallUris(): List<String>
}
