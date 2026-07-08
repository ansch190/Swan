package com.schwanitz.domain.model

data class ArtistImage(
    val artistName: String,
    val discogsArtistId: Long?,
    val imageUrl: String?,
    val localUri: String?,
    val lastUpdated: Long
)
