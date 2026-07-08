package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index("sourceId"),
        Index("album", "artist")
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val sourceId: String,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    val albumArtist: String = "",
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val trackRaw: String = "",
    val discRaw: String = "",
    val year: Int = 0,
    val genre: String = "",
    val mimeType: String = "",
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val fileSize: Long = 0L,
    val tagVersion: String = ""
)
