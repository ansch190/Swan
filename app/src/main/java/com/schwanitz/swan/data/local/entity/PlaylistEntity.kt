package com.schwanitz.swan.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String, // UUID als String
    val name: String,
    val createdAt: Long // Unix-Timestamp in Millisekunden
)