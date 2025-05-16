package com.schwanitz.swan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.schwanitz.swan.data.local.entity.LibraryPathEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryPathDao {
    @Query("SELECT * FROM library_paths")
    fun getAllPaths(): Flow<List<LibraryPathEntity>>

    @Insert
    suspend fun insertPath(path: LibraryPathEntity)

    @Query("DELETE FROM library_paths WHERE uri = :uri")
    suspend fun deletePath(uri: String)
}