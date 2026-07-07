package com.schwanitz.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "song_artwork",
    primaryKeys = ["songId", "sortOrder"]
)
data class SongArtworkEntity(
    val songId: String,
    val sortOrder: Int,
    val pictureType: String,
    val uri: String
)
