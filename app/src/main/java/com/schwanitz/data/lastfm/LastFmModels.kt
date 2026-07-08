package com.schwanitz.data.lastfm

import kotlinx.serialization.Serializable

@Serializable
data class LastFmArtistResponse(
    val artist: LastFmArtist
)

@Serializable
data class LastFmArtist(
    val name: String,
    val bio: LastFmBio
)

@Serializable
data class LastFmBio(
    val summary: String = "",
    val content: String = ""
)
