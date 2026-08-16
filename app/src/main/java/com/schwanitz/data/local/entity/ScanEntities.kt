package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "scan_sessions", primaryKeys = ["id"])
data class ScanSessionEntity(
    val id: String,
    val sourceId: String,
    val startedAt: Long
)

@Entity(
    tableName = "scan_discovered",
    primaryKeys = ["sessionId", "songId"],
    foreignKeys = [ForeignKey(
        entity = ScanSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ScanDiscoveredEntity(val sessionId: String, val songId: String)

@Entity(
    tableName = "scan_songs",
    primaryKeys = ["sessionId", "songId"],
    foreignKeys = [ForeignKey(
        entity = ScanSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ScanSongEntity(
    val sessionId: String,
    val songId: String,
    val title: String,
    val artistName: String,
    val albumName: String,
    val albumArtist: String,
    val durationMs: Long,
    val sourceId: String,
    val discNumber: Int,
    val trackNumber: Int,
    val year: Int,
    val genre: String,
    val mimeType: String,
    val sampleRate: Int,
    val bitrate: Int,
    val fileSize: Long,
    val tagVersion: String
)

@Entity(
    tableName = "scan_artworks",
    primaryKeys = ["sessionId", "albumName", "albumArtist", "year", "sortOrder"],
    foreignKeys = [ForeignKey(
        entity = ScanSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class ScanArtworkEntity(
    val sessionId: String,
    val albumName: String,
    val albumArtist: String,
    val year: Int,
    val sortOrder: Int,
    val uriLarge: String,
    val uriSmall: String?
)
