package com.schwanitz.domain.model

data class AlbumSeries(
    val id: Long,
    val name: String,
    val albumCount: Int = 0
)
