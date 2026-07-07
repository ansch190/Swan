package com.schwanitz.domain.model

data class SongArtwork(
    val songId: String,
    val sortOrder: Int,
    val pictureType: String,
    val uri: String
)
