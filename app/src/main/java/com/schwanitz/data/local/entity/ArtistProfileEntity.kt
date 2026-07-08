package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_profiles")
data class ArtistProfileEntity(
    @PrimaryKey val artistName: String,
    val profile: String,
    val lastUpdated: Long
)
