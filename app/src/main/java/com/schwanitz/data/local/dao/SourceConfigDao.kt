package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.SourceConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceConfigDao {
    @Query("SELECT * FROM source_configs ORDER BY type ASC, name ASC")
    fun getAll(): Flow<List<SourceConfigEntity>>

    @Query("SELECT * FROM source_configs ORDER BY type ASC, name ASC")
    suspend fun getAllOnce(): List<SourceConfigEntity>

    @Query("SELECT * FROM source_configs WHERE id = :id")
    suspend fun getById(id: String): SourceConfigEntity?

    @Query("SELECT * FROM source_configs WHERE isEnabled = 1 ORDER BY type ASC, name ASC")
    suspend fun getEnabled(): List<SourceConfigEntity>

    @Upsert
    suspend fun upsert(config: SourceConfigEntity)

    @Delete
    suspend fun delete(config: SourceConfigEntity)

    @Query("UPDATE source_configs SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
