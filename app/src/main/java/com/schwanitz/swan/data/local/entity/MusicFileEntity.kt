package com.schwanitz.swan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_files",
    foreignKeys = [ForeignKey(
        entity = LibraryPathEntity::class,
        parentColumns = ["uri"],
        childColumns = ["libraryPathUri"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["libraryPathUri"])]
)
data class MusicFileEntity(
    @PrimaryKey val uri: String,
    val libraryPathUri: String,
    val name: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val discNumber: String?,
    val trackNumber: String?,
    val year: String?,
    val genre: String?,
    val fileSize: Int,
    val audioCodec: String?,
    val sampleRate: Int,
    val bitrate: Long,
    val tagVersion: String?
)