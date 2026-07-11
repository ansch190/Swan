package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schwanitz.data.local.entity.AlbumEntity

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE name = :name AND albumArtist = :albumArtist LIMIT 1")
    suspend fun findByNameAndAlbumArtist(name: String, albumArtist: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM album_song_mapping)")
    suspend fun deleteOrphaned()
}
