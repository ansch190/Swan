package com.schwanitz.domain.model

data class Artist(
    val id: Long = 0,
    val name: String,
    val biography: String? = null,
    val biographyLastUpdated: Long = 0L
)
