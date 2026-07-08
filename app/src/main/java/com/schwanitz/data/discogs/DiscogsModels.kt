package com.schwanitz.data.discogs

import kotlinx.serialization.Serializable

@Serializable
data class DiscogsSearchResponse(
    val results: List<DiscogsSearchResult>
)

@Serializable
data class DiscogsSearchResult(
    val id: Long,
    val title: String,
    val cover_image: String = ""
)

@Serializable
data class DiscogsArtistResponse(
    val id: Long,
    val name: String,
    val profile: String = "",
    val images: List<DiscogsImage> = emptyList()
)

@Serializable
data class DiscogsImage(
    val uri: String,
    val uri150: String = "",
    val type: String = ""
)
