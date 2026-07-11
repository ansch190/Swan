package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index("sourceId"),
        Index("artistId"),
        Index("isActive"),
        Index("genre"),
        Index("isFavorite")
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistId: Long? = null,
    val durationMs: Long,
    val sourceId: String,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    val genre: String = "",
    val tagVersion: String = ""
)
