package com.schwanitz.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artist_pics",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("artistId")]
)
data class ArtistPicEntity(
    @PrimaryKey val artistId: Long,
    val uriSmall: String?,
    val uriLarge: String?,
    val imageUrl: String? = null,
    val imageLastUpdated: Long = 0L
)
