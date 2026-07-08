package com.schwanitz.data.genius

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeniusSearchResponse(
    val meta: GeniusMeta,
    val response: GeniusSearchResults
)

@Serializable
data class GeniusMeta(
    val status: Int
)

@Serializable
data class GeniusSearchResults(
    val hits: List<GeniusHit>
)

@Serializable
data class GeniusHit(
    val result: GeniusSongResult
)

@Serializable
data class GeniusSongResult(
    val id: Long,
    val title: String,
    @kotlinx.serialization.SerialName("artist_names")
    val artistNames: String = "",
    @kotlinx.serialization.SerialName("api_path")
    val apiPath: String = "",
    val url: String = "",
    @kotlinx.serialization.SerialName("song_art_image_url")
    val songArtImageUrl: String? = null
)
