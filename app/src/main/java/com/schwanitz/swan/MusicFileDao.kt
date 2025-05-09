package com.schwanitz.swan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicFileDao {
    @Query("SELECT * FROM music_files")
    fun getAllFiles(): Flow<List<MusicFileEntity>>

    @Insert
    suspend fun insertFiles(files: List<MusicFileEntity>)

    @Query("DELETE FROM music_files WHERE libraryPathUri = :libraryPathUri")
    suspend fun deleteFilesByPath(libraryPathUri: String)
}