package com.schwanitz.swan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songUri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MusicFileEntity::class,
            parentColumns = ["uri"],
            childColumns = ["songUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["songUri"])]
)
data class PlaylistSongEntity(
    val playlistId: String,
    val songUri: String
)