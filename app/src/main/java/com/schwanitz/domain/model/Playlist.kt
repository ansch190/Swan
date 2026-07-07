package com.schwanitz.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val songs: List<Song> = emptyList()
)
