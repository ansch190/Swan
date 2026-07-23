package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.AlbumEntity

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE name = :name AND albumArtist = :albumArtist LIMIT 1")
    suspend fun findByNameAndAlbumArtist(name: String, albumArtist: String): AlbumEntity?

    @Upsert
    suspend fun upsert(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM album_song_mapping)")
    suspend fun deleteOrphaned()
}
