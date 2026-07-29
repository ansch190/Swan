package com.schwanitz.data.repository

import android.content.Context
import com.schwanitz.BuildConfig
import com.schwanitz.data.discogs.DiscogsApiService
import com.schwanitz.data.lastfm.LastFmApiService
import com.schwanitz.data.local.CredentialStore
import com.schwanitz.data.local.LanguagePreferences
import com.schwanitz.data.local.dao.ArtistDao
import com.schwanitz.data.local.dao.ArtistPicDao
import com.schwanitz.data.local.entity.ArtistEntity
import com.schwanitz.data.local.entity.ArtistPicEntity
import com.schwanitz.data.source.ArtistImageCache
import com.schwanitz.data.source.WebDavArtistDataProvider
import com.schwanitz.domain.model.Artist
import com.schwanitz.domain.model.ArtistBiographyResult
import com.schwanitz.domain.model.BioSource
import com.schwanitz.domain.repository.ArtistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val artistDao: ArtistDao,
    private val artistPicDao: ArtistPicDao,
    private val discogsApiService: DiscogsApiService,
    private val lastFmApiService: LastFmApiService,
    private val webDavArtistDataProvider: WebDavArtistDataProvider,
    private val languagePreferences: LanguagePreferences,
    private val credentialStore: CredentialStore,
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

    override suspend fun clearAllArtistImages() {
        withContext(Dispatchers.IO) { ArtistImageCache.clearAll(context) }
        artistPicDao.deleteAll()
    }

    override suspend fun clearAllArtistBiographies() {
        artistDao.clearAllBiographies()
    }

    private suspend fun getOrCreatePic(artistId: Long): ArtistPicEntity? {
        val existing = artistPicDao.getByArtistId(artistId)
        if (existing != null) {
            Timber.d("Artist image cache HIT for artistId=%d", artistId)
            return existing
        }

        val artist = artistDao.getById(artistId) ?: return null

        if (webDavArtistDataProvider.isConfigured()) {
            Timber.d("Fetching artist image from WebDAV for '%s'", artist.name)
            val bytes = webDavArtistDataProvider.fetchImage(artist.name)
            if (bytes != null) {
                val picResult = ArtistImageCache.saveScaled(bytes, context, artist.name)
                val picEntity = ArtistPicEntity(
                    artistId = artistId,
                    uriSmall = picResult.smallUri,
                    uriLarge = picResult.largeUri,
                    imageUrl = null,
                    imageLastUpdated = System.currentTimeMillis()
                )
                artistPicDao.upsert(picEntity)
                return picEntity
            }
        }

        val discogsKey = credentialStore.getApiDiscogsKey()?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DISCOGS_CONSUMER_KEY
        if (discogsKey.isBlank()) return null

        Timber.d("Fetching artist image from Discogs for '%s'", artist.name)

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

    override suspend fun getArtistBiography(artistId: Long): ArtistBiographyResult? {
        val artist = artistDao.getById(artistId) ?: return null

        if (artist.biography != null && !isBiographyExpired(artist)) {
            Timber.d("Artist biography cache HIT for '%s'", artist.name)
            val source = if (artist.biographyLastUpdated == Long.MAX_VALUE) BioSource.WEBDAV else BioSource.LASTFM
            return ArtistBiographyResult(artist.biography, source)
        }

        if (webDavArtistDataProvider.isConfigured()) {
            Timber.d("Fetching artist biography from WebDAV for '%s'", artist.name)
            val langCode = when (languagePreferences.getLanguageSync()) {
                LanguagePreferences.GERMAN -> "de"
                LanguagePreferences.ENGLISH -> "en"
                else -> if (java.util.Locale.getDefault().language == "de") "de" else "en"
            }
            val bio = webDavArtistDataProvider.fetchBio(artist.name, langCode)
            if (bio != null) {
                artistDao.upsert(artist.copy(
                    biography = bio,
                    biographyLastUpdated = Long.MAX_VALUE
                ))
                return ArtistBiographyResult(bio, BioSource.WEBDAV)
            }
        }

        Timber.d("Fetching artist biography from Last.fm for '%s'", artist.name)

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

        return ArtistBiographyResult(cleanContent, BioSource.LASTFM)
    }

    private fun isBiographyExpired(artist: ArtistEntity): Boolean {
        if (artist.biographyLastUpdated == Long.MAX_VALUE) return false
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
