package com.schwanitz.domain.model

data class Album(
    val id: Long = 0,
    val name: String,
    val albumArtist: String = "",
    val year: Int = 0,
    val albumArtUri: String? = null
)
