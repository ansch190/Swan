package com.schwanitz.swan.data.local.entity

import android.net.Uri
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.schwanitz.swan.domain.model.MusicFile

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
    val year: Int?,
    val genre: String?,
    val fileSize: Long,
    val audioCodec: String?,
    val sampleRate: Int,
    val bitrate: Long,
    val tagVersion: String?
)

fun MusicFileEntity.toDomainModel(): MusicFile = MusicFile(
    uri = Uri.parse(this.uri),
    name = this.name,
    title = this.title,
    artist = this.artist,
    album = this.album,
    albumArtist = this.albumArtist,
    discNumber = this.discNumber,
    trackNumber = this.trackNumber,
    year = this.year,
    genre = this.genre,
    fileSize = this.fileSize,
    audioCodec = this.audioCodec,
    sampleRate = this.sampleRate,
    bitrate = this.bitrate,
    tagVersion = this.tagVersion
)
