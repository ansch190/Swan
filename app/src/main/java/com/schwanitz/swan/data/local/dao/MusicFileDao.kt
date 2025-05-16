package com.schwanitz.swan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.schwanitz.swan.data.local.entity.MusicFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicFileDao {
    @Query("SELECT * FROM music_files")
    fun getAllFiles(): Flow<List<MusicFileEntity>>

    @Insert
    suspend fun insertFiles(files: List<MusicFileEntity>)

    @Query("DELETE FROM music_files WHERE libraryPathUri = :libraryPathUri")
    suspend fun deleteFilesByPath(libraryPathUri: String)

    @Query("SELECT * FROM music_files WHERE uri = :uri")
    fun getFileByUri(uri: String): Flow<MusicFileEntity?>

}