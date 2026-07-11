package com.schwanitz.domain.model

data class Song(
    val id: String,
    val title: String,
    val artistId: Long? = null,
    val artistName: String = "",
    val albumId: Long? = null,
    val albumName: String = "",
    val albumArtistName: String = "",
    val durationMs: Long,
    val albumArtUri: String? = null,
    val albumArtUriLarge: String? = null,
    val sourceId: String,
    val isFavorite: Boolean = false,
    val isActive: Boolean = true,
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "",
    val mimeType: String = "",
    val sampleRate: Int = 0,
    val bitrate: Int = 0,
    val fileSize: Long = 0L,
    val tagVersion: String = ""
) {
    val filePath: String get() = id
}
