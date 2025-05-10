package com.schwanitz.swan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists WHERE artistName = :artistName")
    suspend fun getArtist(artistName: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: ArtistEntity)

    @Query("UPDATE artists SET imageUrl = :imageUrl WHERE artistName = :artistName AND (:imageUrl IS NOT NULL OR imageUrl IS NULL)")
    suspend fun updateArtistImage(artistName: String, imageUrl: String?)

    @Query("SELECT * FROM artists")
    fun getAllArtists(): Flow<List<ArtistEntity>>
}