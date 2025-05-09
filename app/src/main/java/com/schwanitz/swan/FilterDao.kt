package com.schwanitz.swan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters")
    fun getAllFilters(): Flow<List<FilterEntity>>

    @Insert
    suspend fun insertFilter(filter: FilterEntity)

    @Query("DELETE FROM filters WHERE criterion = :criterion")
    suspend fun deleteFilter(criterion: String)
}