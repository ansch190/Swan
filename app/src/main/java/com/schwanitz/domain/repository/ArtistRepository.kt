package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Artist

interface ArtistRepository {
    suspend fun getArtistByName(name: String): Artist?
    suspend fun getArtistImageSmall(artistId: Long): String?
    suspend fun getArtistImageLarge(artistId: Long): String?
    suspend fun getArtistBiography(artistId: Long): String?
}
