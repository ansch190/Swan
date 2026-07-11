package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Artist

interface ArtistRepository {
    suspend fun getArtist(artistId: Long): Artist?
    suspend fun getArtistByName(name: String): Artist?
    suspend fun getAllArtists(): List<Artist>
    suspend fun getArtistImageSmall(artistId: Long): String?
    suspend fun getArtistImageLarge(artistId: Long): String?
    suspend fun getArtistBiography(artistId: Long): String?
}
