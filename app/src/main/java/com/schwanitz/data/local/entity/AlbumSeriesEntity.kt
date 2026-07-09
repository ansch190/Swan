package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "album_series")
data class AlbumSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
