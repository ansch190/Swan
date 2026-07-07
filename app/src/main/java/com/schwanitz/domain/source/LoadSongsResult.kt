package com.schwanitz.domain.source

import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork

data class LoadSongsResult(
    val songs: List<Song>,
    val artworks: List<SongArtwork>
)
