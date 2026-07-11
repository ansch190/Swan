package com.schwanitz.domain.model

data class Playlist(
    val id: Long = 0,
    val name: String,
    val songs: List<Song> = emptyList()
)
