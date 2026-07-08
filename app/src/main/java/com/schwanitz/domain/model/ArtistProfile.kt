package com.schwanitz.domain.model

data class ArtistProfile(
    val artistName: String,
    val summary: String?,
    val content: String,
    val source: String,
    val lastUpdated: Long
)
