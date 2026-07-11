package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import com.schwanitz.data.local.entity.SongTechnicalInfoEntity

@Dao
interface SongTechnicalInfoDao {

    @Upsert
    suspend fun upsertAll(technicalInfos: List<SongTechnicalInfoEntity>)
}
