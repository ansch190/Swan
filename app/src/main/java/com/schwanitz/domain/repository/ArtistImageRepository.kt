package com.schwanitz.domain.repository

interface ArtistImageRepository {
    suspend fun getArtistImage(artistName: String): String?
}
