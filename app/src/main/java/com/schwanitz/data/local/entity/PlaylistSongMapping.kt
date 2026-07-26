package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_song_mapping",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId"), Index("playlistId")]
)
data class PlaylistSongMapping(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: String,
    val orderIndex: Int
)
