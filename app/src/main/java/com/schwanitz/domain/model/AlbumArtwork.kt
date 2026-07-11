package com.schwanitz.domain.model

data class AlbumArtwork(
    val albumId: Long,
    val sortOrder: Int,
    val uriLarge: String,
    val uriSmall: String? = null
)
