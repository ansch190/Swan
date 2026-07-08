package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artist_images")
data class ArtistImageEntity(
    @PrimaryKey val artistName: String,
    val discogsArtistId: Long?,
    val imageUrl: String?,
    val localUri: String?,
    val lastUpdated: Long
)
