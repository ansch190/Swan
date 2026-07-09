package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "album_series_mapping",
    foreignKeys = [
        ForeignKey(
            entity = AlbumSeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("seriesId")]
)
data class AlbumSeriesMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val albumName: String,
    val volumeNumber: Int
)
