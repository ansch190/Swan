package com.schwanitz.swan

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val artistName: String,
    val imageUrl: String?
)