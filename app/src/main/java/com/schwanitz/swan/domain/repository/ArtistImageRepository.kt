package com.schwanitz.swan.domain.repository

interface ArtistImageRepository {

    suspend fun getArtistImageUrl(artistName: String): String?
}
