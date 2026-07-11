package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schwanitz.data.local.entity.AlbumSongMappingEntity

@Dao
interface AlbumSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mappings: List<AlbumSongMappingEntity>)

    @Query("DELETE FROM album_song_mapping WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM album_song_mapping WHERE albumId = :albumId")
    suspend fun deleteByAlbumId(albumId: Long)

    @Query("DELETE FROM album_song_mapping")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM album_song_mapping WHERE albumId = :albumId AND discNumber = :discNumber")
    suspend fun getTrackTotal(albumId: Long, discNumber: Int): Int

    @Query("SELECT COUNT(DISTINCT discNumber) FROM album_song_mapping WHERE albumId = :albumId")
    suspend fun getDiscTotal(albumId: Long): Int
}
