package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "album_artwork",
    primaryKeys = ["albumId", "sortOrder"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("albumId")]
)
data class AlbumArtworkEntity(
    val albumId: Long,
    val sortOrder: Int,
    val uriLarge: String,
    val uriSmall: String? = null
)
