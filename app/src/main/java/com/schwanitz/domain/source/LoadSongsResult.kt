package com.schwanitz.domain.source

import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song

data class LoadSongsResult(
    val songs: List<Song>,
    val albums: List<Album>,
    val artworks: Map<String, List<AlbumArtwork>>
)
