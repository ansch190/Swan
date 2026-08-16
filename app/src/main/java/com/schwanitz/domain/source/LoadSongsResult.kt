package com.schwanitz.domain.source

import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song

data class AlbumKey(
    val name: String,
    val albumArtist: String,
    val year: Int
)

data class ScanBatch(
    val songs: List<Song>,
    val albums: List<Album>,
    val artworks: Map<AlbumKey, List<AlbumArtwork>>
)

sealed interface ScanEvent {
    data class Discovered(val songIds: List<String>) : ScanEvent
    data class Parsed(val batch: ScanBatch) : ScanEvent
}

data class ScanSummary(
    val total: Int,
    val succeeded: Int,
    val failedIds: Set<String> = emptySet()
)
