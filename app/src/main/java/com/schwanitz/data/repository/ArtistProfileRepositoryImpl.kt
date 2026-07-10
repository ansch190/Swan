package com.schwanitz.data.repository

import android.util.Log
import com.schwanitz.data.local.dao.ArtistProfileDao
import com.schwanitz.data.local.entity.ArtistProfileEntity
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.data.local.converter.toEntity
import com.schwanitz.domain.model.ArtistProfile
import com.schwanitz.domain.repository.ArtistProfileRepository
import com.schwanitz.domain.source.ArtistProfileProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistProfileRepositoryImpl @Inject constructor(
    private val provider: ArtistProfileProvider,
    private val profileDao: ArtistProfileDao
) : ArtistProfileRepository {

    override suspend fun getArtistProfile(artistName: String): ArtistProfile? {
        if (artistName.isBlank()) {
            Log.e("ArtistProfile", "Artist name is blank - skipping")
            return null
        }

        val cached = profileDao.get(artistName)
        if (cached != null && !isExpired(cached)) {
            Log.e("ArtistProfile", "Cache hit for '$artistName'")
            return cached.toDomain()
        }

        Log.e("ArtistProfile", "Fetching from provider for '$artistName'")
        val profile = provider.fetchProfile(artistName)
        if (profile != null) {
            profileDao.upsert(profile.toEntity())
            Log.e("ArtistProfile", "Cached profile for '$artistName'")
        } else {
            Log.e("ArtistProfile", "No profile found for '$artistName'")
        }
        return profile
    }

    private fun isExpired(entity: ArtistProfileEntity): Boolean {
        val ttlMs = 6L * 30L * 24L * 60L * 60L * 1000L // ~6 Monate
        return System.currentTimeMillis() - entity.lastUpdated > ttlMs
    }
}
