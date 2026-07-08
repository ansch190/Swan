package com.schwanitz.domain.repository

import com.schwanitz.domain.model.ArtistProfile

interface ArtistProfileRepository {
    suspend fun getArtistProfile(artistName: String): ArtistProfile?
}
