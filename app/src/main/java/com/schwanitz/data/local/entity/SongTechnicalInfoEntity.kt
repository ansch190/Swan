package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_technical_info",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId")]
)
data class SongTechnicalInfoEntity(
    @PrimaryKey val songId: String,
    val fileSize: Long = 0L,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val mimeType: String = ""
)
