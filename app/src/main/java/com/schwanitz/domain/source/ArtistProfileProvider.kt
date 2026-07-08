package com.schwanitz.domain.source

import com.schwanitz.domain.model.ArtistProfile

interface ArtistProfileProvider {
    suspend fun fetchProfile(artistName: String): ArtistProfile?
}
