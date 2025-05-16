package com.schwanitz.swan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
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
    indices = [Index(value = ["songUri"]), Index(value = ["playlistId"])]
)
data class PlaylistSongEntity(
    @androidx.room.PrimaryKey val id: String, // Neu: Eindeutige ID für jeden Eintrag
    val playlistId: String,
    val songUri: String,
    val position: Int // Neu: Reihenfolge innerhalb der Playlist
)