package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Artist
import com.schwanitz.domain.model.ArtistBiographyResult

interface ArtistRepository {
    suspend fun getArtistByName(name: String): Artist?
    suspend fun getArtistImageSmall(artistId: Long): String?
    suspend fun getArtistImageLarge(artistId: Long): String?
    suspend fun getArtistBiography(artistId: Long): ArtistBiographyResult?
    suspend fun clearAllArtistImages()
    suspend fun clearAllArtistBiographies()
}
