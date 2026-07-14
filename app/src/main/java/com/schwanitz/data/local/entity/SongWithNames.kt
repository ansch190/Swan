package com.schwanitz.data.local.entity

import androidx.room.DatabaseView

@DatabaseView("""
    SELECT s.id, s.title, s.artistId, asm.albumId,
        s.durationMs, s.sourceId, s.isFavorite, s.isActive,
        asm.discNumber, asm.trackNumber,
        al.year, s.genre, s.tagVersion,
        sti.mimeType, sti.sampleRate, sti.bitrate, sti.fileSize,
        a.name as artistName,
        al.name as albumName,
        aw.uriSmall as albumArtUri,
        aw.uriLarge as albumArtUriLarge,
        al.albumArtist as albumArtistName
    FROM songs s
    INNER JOIN album_song_mapping asm ON s.id = asm.songId
    LEFT JOIN artists a ON s.artistId = a.id
    LEFT JOIN albums al ON asm.albumId = al.id
    LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
    LEFT JOIN song_technical_info sti ON s.id = sti.songId
""")
data class SongWithNames(
    val id: String,
    val title: String,
    val artistId: Long?,
    val artistName: String?,
    val albumId: Long?,
    val albumName: String?,
    val albumArtistName: String?,
    val durationMs: Long,
    val albumArtUri: String?,
    val albumArtUriLarge: String?,
    val sourceId: String,
    val isFavorite: Boolean,
    val isActive: Boolean,
    val discNumber: Int,
    val trackNumber: Int,
    val year: Int,
    val genre: String,
    val mimeType: String,
    val sampleRate: Int,
    val bitrate: Int,
    val fileSize: Long,
    val tagVersion: String
)
