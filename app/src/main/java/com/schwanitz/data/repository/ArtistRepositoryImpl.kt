package com.schwanitz.data.repository

import android.content.Context
import com.schwanitz.BuildConfig
import com.schwanitz.data.discogs.DiscogsApiService
import com.schwanitz.data.lastfm.LastFmApiService
import com.schwanitz.data.local.dao.ArtistDao
import com.schwanitz.data.local.dao.ArtistPicDao
import com.schwanitz.data.local.entity.ArtistEntity
import com.schwanitz.data.local.entity.ArtistPicEntity
import com.schwanitz.data.source.ArtistImageCache
import com.schwanitz.domain.model.Artist
import com.schwanitz.domain.repository.ArtistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
    private val artistPicDao: ArtistPicDao,
    private val discogsApiService: DiscogsApiService,
    private val lastFmApiService: LastFmApiService,
    @ApplicationContext private val context: Context
) : ArtistRepository {

    override suspend fun getArtistByName(name: String): Artist? {
        return artistDao.findByName(name)?.toDomain()
    }

    override suspend fun getArtistImageSmall(artistId: Long): String? {
        return getOrCreatePic(artistId)?.uriSmall
    }

    override suspend fun getArtistImageLarge(artistId: Long): String? {
        return getOrCreatePic(artistId)?.uriLarge
    }

    private suspend fun getOrCreatePic(artistId: Long): ArtistPicEntity? {
        val existing = artistPicDao.getByArtistId(artistId)
        if (existing != null) return existing

        val artist = artistDao.getById(artistId) ?: return null
        if (BuildConfig.DISCOGS_CONSUMER_KEY.isBlank()) return null

        val searchResult = discogsApiService.searchArtist(artist.name) ?: return null
        val discogsId = searchResult.results.firstOrNull()?.id ?: return null

        val detail = discogsApiService.getArtistDetail(discogsId) ?: return null
        val imageUrl = detail.images.firstOrNull { it.type == "primary" }?.uri
            ?: detail.images.firstOrNull()?.uri
            ?: return null

        val bytes = discogsApiService.downloadImage(imageUrl) ?: return null
        val picResult = ArtistImageCache.saveScaled(bytes, context, artist.name)

        val picEntity = ArtistPicEntity(
            artistId = artistId,
            uriSmall = picResult.smallUri,
            uriLarge = picResult.largeUri,
            imageUrl = imageUrl,
            imageLastUpdated = System.currentTimeMillis()
        )
        artistPicDao.upsert(picEntity)

        return picEntity
    }

    override suspend fun getArtistBiography(artistId: Long): String? {
        val artist = artistDao.getById(artistId) ?: return null

        if (artist.biography != null && !isBiographyExpired(artist)) {
            return artist.biography
        }

        val artistInfo = lastFmApiService.getArtistInfo(artist.name) ?: return null
        val bio = artistInfo.bio
        if (bio.content.isBlank()) return null

        val cleanContent = android.text.Html.fromHtml(
            bio.content, android.text.Html.FROM_HTML_MODE_LEGACY
        ).toString().trim()

        artistDao.upsert(artist.copy(
            biography = cleanContent,
            biographyLastUpdated = System.currentTimeMillis()
        ))

        return cleanContent
    }

    private fun isBiographyExpired(artist: ArtistEntity): Boolean {
        val ttlMs = 6L * 30L * 24L * 60L * 60L * 1000L
        return System.currentTimeMillis() - artist.biographyLastUpdated > ttlMs
    }

    private fun ArtistEntity.toDomain(): Artist = Artist(
        id = id,
        name = name,
        biography = biography,
        biographyLastUpdated = biographyLastUpdated
    )
}
