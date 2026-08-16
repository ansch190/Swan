package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["name", "albumArtist", "year"], unique = true, name = "index_albums_identity"),
        Index("albumArtist")
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val albumArtist: String = "",
    val year: Int = 0
)
