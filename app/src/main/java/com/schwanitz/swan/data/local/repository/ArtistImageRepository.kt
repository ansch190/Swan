package com.schwanitz.swan.data.local.repository

import com.schwanitz.swan.util.Logger
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.ArtistEntity
import com.schwanitz.swan.data.remote.api.ApiClient
import com.schwanitz.swan.domain.repository.ArtistImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class ArtistImageRepository(private val db: AppDatabase) : ArtistImageRepository {

    private val TAG = "ArtistImageRepository"

    override suspend fun getArtistImageUrl(artistName: String): String? {
        val cachedArtist = db.artistDao().getArtist(artistName)
        if (cachedArtist != null && cachedArtist.imageUrl != null) {
            Logger.d(TAG, "Found cached image URL for artist: $artistName, URL: ${cachedArtist.imageUrl}")
            return cachedArtist.imageUrl
        }
        if (cachedArtist != null) {
            Logger.d(TAG, "Cached artist $artistName has no image URL, skipping API call")
            return null
        }

        val attempts = listOf(
            artistName,
            artistName.lowercase(),
            artistName.replace(" ", ""),
            artistName.replace(" ", "-")
        )

        for (attempt in attempts) {
            val searchQueryForLink = attempt.replace(" ", "-").replace("'", "")
            val encodedSearchQuery = URLEncoder.encode(attempt, StandardCharsets.UTF_8.toString())
            Logger.d(TAG, "Trying artist name: $artistName -> $attempt (encoded: $encodedSearchQuery, link format: $searchQueryForLink)")

            try {
                val searchUrl = "https://www.theaudiodb.com"
                val browseUrl = "https://www.theaudiodb.com/browse.php"
                Logger.d(TAG, "Fetching main page: $searchUrl")
                val doc = withContext(Dispatchers.IO) {
                    Jsoup.connect(searchUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get()
                }
                Logger.d(TAG, "Fetched main page for $artistName: ${doc.title()}")

                val searchForm = doc.select("form[role=form][action=/browse.php][method=post]").first()
                if (searchForm != null) {
                    Logger.d(TAG, "Found search form for $artistName")
                    val searchResults = withContext(Dispatchers.IO) {
                        Jsoup.connect(browseUrl)
                            .data("search", attempt)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(10000)
                            .post()
                    }
                    Logger.d(TAG, "Fetched search results for $artistName (attempt: $attempt): ${searchResults.title()}")

                    val artistLinks = searchResults.select("a[href^=/artist/]")
                    if (artistLinks.isNotEmpty()) {
                        Logger.d(TAG, "Found ${artistLinks.size} artist links for $artistName")
                        for (link in artistLinks) {
                            val href = link.absUrl("href")
                            Logger.d(TAG, "Checking link: $href")

                            val pattern = Pattern.compile("/artist/(\\d+)-${Pattern.quote(searchQueryForLink)}$", Pattern.CASE_INSENSITIVE)
                            val matcher = pattern.matcher(href)
                            if (matcher.find()) {
                                val artistId = matcher.group(1)
                                Logger.d(TAG, "Extracted artist ID for $artistName: $artistId")

                                val safeArtistId = artistId ?: continue
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                        ApiClient.theAudioDBService.getArtistById(safeArtistId)
                                    }
                                    Logger.d(TAG, "API response for $artistName (ID: $safeArtistId): artists=${response.artists?.map { "${it.strArtist}: ${it.strArtistThumb}" } ?: "null"}")
                                    val imageUrl = response.artists?.firstOrNull()?.strArtistThumb?.takeIf { it.isNotBlank() }
                                    if (imageUrl != null) {
                                        db.artistDao().insertArtist(ArtistEntity(artistName, imageUrl))
                                        Logger.d(TAG, "Fetched and cached image URL for artist: $artistName, URL: $imageUrl")
                                        return imageUrl
                                    } else {
                                        Logger.w(TAG, "No image found in API response for artist: $artistName (ID: $safeArtistId)")
                                    }
                                } catch (e: Exception) {
                                    Logger.e(TAG, "Failed to fetch artist image from API for $artistName (ID: $safeArtistId): ${e.message}", e)
                                }
                            }
                        }
                    } else {
                        Logger.w(TAG, "No artist links found on search results page for $artistName (attempt: $attempt)")
                    }
                } else {
                    Logger.e(TAG, "Search form not found on main page for $artistName")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to fetch artist page for $artistName with attempt $attempt: ${e.message}", e)
            }
        }

        db.artistDao().insertArtist(ArtistEntity(artistName, null))
        Logger.w(TAG, "No image found for artist: $artistName after all attempts, caching null")
        return null
    }
}
