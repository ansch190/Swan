package com.schwanitz.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.schwanitz.BuildConfig
import com.schwanitz.data.discogs.DiscogsApiService
import com.schwanitz.data.local.dao.ArtistImageDao
import com.schwanitz.data.local.entity.ArtistImageEntity
import com.schwanitz.data.source.ArtistImageCache
import com.schwanitz.domain.repository.ArtistImageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImageRepositoryImpl @Inject constructor(
    private val apiService: DiscogsApiService,
    private val artistImageDao: ArtistImageDao,
    @ApplicationContext private val context: Context
) : ArtistImageRepository {

    override suspend fun getArtistImage(artistName: String): String? {
        Log.e("ArtistImage", "getArtistImage: start for '$artistName'")

        if (artistName.isBlank()) {
            Log.e("ArtistImage", "Artist name is blank - skipping")
            return null
        }

        if (BuildConfig.DISCOGS_CONSUMER_KEY.isBlank()) {
            Log.e("ArtistImage", "DISCOGS_CONSUMER_KEY is empty - set discogsKey in local.properties")
            return null
        }

        val cached = artistImageDao.get(artistName)
        if (cached?.localUri != null) {
            val filePath = Uri.parse(cached.localUri).path
            val exists = filePath != null && File(filePath).exists()
            Log.e("ArtistImage", "Cache hit for '$artistName': localUri=${cached.localUri}, filePath=$filePath, exists=$exists")
            if (exists) {
                return cached.localUri
            }
        } else {
            Log.e("ArtistImage", "No cache entry for '$artistName'")
        }

        Log.e("ArtistImage", "Searching Discogs for '$artistName'...")
        val searchResult = apiService.searchArtist(artistName)
        if (searchResult == null) {
            Log.e("ArtistImage", "Search returned null for '$artistName'")
            return null
        }
        val artistId = searchResult.results.firstOrNull()?.id
        if (artistId == null) {
            Log.e("ArtistImage", "No results found for '$artistName'")
            return null
        }
        Log.e("ArtistImage", "Found artist id=$artistId, title=${searchResult.results.first().title}")

        Log.e("ArtistImage", "Fetching detail for artist $artistId...")
        val detail = apiService.getArtistDetail(artistId)
        if (detail == null) {
            Log.e("ArtistImage", "Artist detail returned null for id=$artistId")
            return null
        }
        Log.e("ArtistImage", "Artist detail: name=${detail.name}, images=${detail.images.size}")

        val imageUrl = detail.images.firstOrNull { it.type == "primary" }?.uri
            ?: detail.images.firstOrNull()?.uri
        if (imageUrl == null) {
            Log.e("ArtistImage", "No images in artist detail for '$artistName'")
            return null
        }
        Log.e("ArtistImage", "Downloading image from $imageUrl")

        val bytes = apiService.downloadImage(imageUrl)
        if (bytes == null) {
            Log.e("ArtistImage", "Image download returned null for $imageUrl")
            return null
        }
        Log.e("ArtistImage", "Downloaded ${bytes.size} bytes")

        val localUri = ArtistImageCache.save(bytes, context, artistName)
        Log.e("ArtistImage", "Saved locally: $localUri")

        artistImageDao.upsert(
            ArtistImageEntity(
                artistName = artistName,
                discogsArtistId = artistId,
                imageUrl = imageUrl,
                localUri = localUri,
                lastUpdated = System.currentTimeMillis()
            )
        )

        return localUri
    }
}
