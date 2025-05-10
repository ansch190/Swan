package com.schwanitz.swan

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class ArtistImageRepository(private val db: AppDatabase) {

    private val TAG = "ArtistImageRepository"

    suspend fun getArtistImageUrl(artistName: String): String? {
        // Prüfe zunächst die lokale Datenbank
        val cachedArtist = db.artistDao().getArtist(artistName)
        if (cachedArtist != null && cachedArtist.imageUrl != null) {
            Log.d(TAG, "Found cached image URL for artist: $artistName, URL: ${cachedArtist.imageUrl}")
            return cachedArtist.imageUrl
        }

        // Versuche mehrere Schreibweisen des Künstlernamens
        val attempts = listOf(
            artistName,
            artistName.lowercase(),
            artistName.replace(" ", ""),
            artistName.replace(" ", "-")
        )

        for (attempt in attempts) {
            // Künstlernamen für den Link anpassen (wie im Java-Code)
            val searchQueryForLink = attempt.replace(" ", "-").replace("'", "")
            val encodedSearchQuery = URLEncoder.encode(attempt, StandardCharsets.UTF_8.toString())
            Log.d(TAG, "Trying artist name: $artistName -> $attempt (encoded: $encodedSearchQuery, link format: $searchQueryForLink)")

            // Schritt 1: Sende die Suchanfrage an das Suchformular
            try {
                val searchUrl = "https://www.theaudiodb.com"
                val browseUrl = "https://www.theaudiodb.com/browse.php"
                Log.d(TAG, "Fetching main page: $searchUrl")
                val doc = withContext(Dispatchers.IO) {
                    Jsoup.connect(searchUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get()
                }
                Log.d(TAG, "Fetched main page for $artistName: ${doc.title()}")

                // Finde das Suchformular
                val searchForm = doc.select("form[role=form][action=/browse.php][method=post]").first()
                if (searchForm != null) {
                    Log.d(TAG, "Found search form for $artistName")
                    // Sende die Suchanfrage
                    val searchResults = withContext(Dispatchers.IO) {
                        Jsoup.connect(browseUrl)
                            .data("search", attempt)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(10000)
                            .post()
                    }
                    Log.d(TAG, "Fetched search results for $artistName (attempt: $attempt): ${searchResults.title()}")

                    // Schritt 2: Suche nach Künstlerlinks
                    val artistLinks = searchResults.select("a[href^=/artist/]")
                    if (artistLinks.isNotEmpty()) {
                        Log.d(TAG, "Found ${artistLinks.size} artist links for $artistName")
                        for (link in artistLinks) {
                            val href = link.absUrl("href")
                            Log.d(TAG, "Checking link: $href")

                            // Regulärer Ausdruck für die Künstler-ID (z. B. /artist/114073-Sido)
                            val pattern = Pattern.compile("/artist/(\\d+)-${Pattern.quote(searchQueryForLink)}$", Pattern.CASE_INSENSITIVE)
                            val matcher = pattern.matcher(href)
                            if (matcher.find()) {
                                val artistId = matcher.group(1)
                                Log.d(TAG, "Extracted artist ID for $artistName: $artistId")

                                // Schritt 3: Frage die API mit der Künstler-ID
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                        ApiClient.theAudioDBService.getArtistById(artistId)
                                    }
                                    Log.d(TAG, "API response for $artistName (ID: $artistId): artists=${response.artists?.map { "${it.strArtist}: ${it.strArtistThumb}" } ?: "null"}")
                                    val imageUrl = response.artists?.firstOrNull()?.strArtistThumb?.takeIf { it.isNotBlank() }
                                    if (imageUrl != null) {
                                        // Cache das Ergebnis in der Datenbank
                                        try {
                                            db.artistDao().insertArtist(ArtistEntity(artistName, imageUrl))
                                            Log.d(TAG, "Fetched and cached image URL for artist: $artistName, URL: $imageUrl")
                                            return imageUrl
                                        } catch (e: android.database.sqlite.SQLiteConstraintException) {
                                            Log.w(TAG, "UNIQUE constraint failed for $artistName, attempting to update existing entry")
                                            // Fallback: Bestehenden Eintrag aktualisieren
                                            db.artistDao().insertArtist(ArtistEntity(artistName, imageUrl))
                                            return imageUrl
                                        }
                                    } else {
                                        Log.w(TAG, "No image found in API response for artist: $artistName (ID: $artistId)")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to fetch artist image from API for $artistName (ID: $artistId): ${e.message}", e)
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "No artist links found on search results page for $artistName (attempt: $attempt)")
                    }
                } else {
                    Log.e(TAG, "Search form not found on main page for $artistName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch artist page for $artistName with attempt $attempt: ${e.message}", e)
            }
        }

        // Cache ein leeres Ergebnis, wenn alle Versuche fehlschlagen
        try {
            db.artistDao().insertArtist(ArtistEntity(artistName, null))
            Log.w(TAG, "No image found for artist: $artistName after all attempts")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            Log.w(TAG, "UNIQUE constraint failed for $artistName when caching null, skipping")
        }
        return null
    }

    // Debugging-Methode zum Löschen des Cache für einen Künstler
    suspend fun clearArtistCache(artistName: String) {
        try {
            db.artistDao().insertArtist(ArtistEntity(artistName, null))
            Log.d(TAG, "Cleared cache for artist: $artistName")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            Log.w(TAG, "UNIQUE constraint failed when clearing cache for $artistName, skipping")
        }
    }
}