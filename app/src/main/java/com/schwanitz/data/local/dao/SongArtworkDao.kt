package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.SongArtworkEntity

@Dao
interface SongArtworkDao {

    @Query("SELECT * FROM song_artwork WHERE songId = :songId ORDER BY sortOrder")
    suspend fun getForSong(songId: String): List<SongArtworkEntity>

    @Upsert
    suspend fun upsertAll(artworks: List<SongArtworkEntity>)

    @Query("SELECT uri FROM song_artwork")
    suspend fun getAllUris(): List<String>

    @Query("DELETE FROM song_artwork WHERE songId IN (SELECT id FROM songs WHERE sourceId = :sourceId)")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM song_artwork")
    suspend fun deleteAll()
}
