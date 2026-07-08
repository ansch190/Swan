package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_lyrics")
data class SongLyricsEntity(
    @PrimaryKey val songId: String,
    val lyrics: String,
    val fetchedAt: Long
)
