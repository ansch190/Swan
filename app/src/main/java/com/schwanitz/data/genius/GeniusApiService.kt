package com.schwanitz.data.genius

import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class GeniusApiService @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiBase = "https://api.genius.com"

    suspend fun searchLyrics(title: String, artist: String): String? {
        if (BuildConfig.GENIUS_ACCESS_TOKEN.isBlank()) {
            Timber.e("GENIUS_ACCESS_TOKEN is empty")
            return null
        }

        val query1 = "$title - $artist"
        Timber.d("Search attempt 1: \"$query1\"")
        val url1 = searchForSongOrNull(query1, title)
        if (url1 != null) return fetchLyricsFromPage(url1)

        val cleanTitle = title.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        if (cleanTitle != title) {
            val query2 = "$cleanTitle - $artist"
            Timber.d("Search attempt 2: \"$query2\"")
            val url2 = searchForSongOrNull(query2, cleanTitle)
            if (url2 != null) return fetchLyricsFromPage(url2)
        }

        Timber.d("No matching song found for \"$title\" - $artist")
        return null
    }

    private suspend fun searchForSongOrNull(rawQuery: String, checkTitle: String): String? {
        val encoded = java.net.URLEncoder.encode(rawQuery, "UTF-8")
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("$apiBase/search?q=$encoded")
                        .header("Authorization", "Bearer ${BuildConfig.GENIUS_ACCESS_TOKEN}")
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string()
                        if (body == null || !response.isSuccessful) {
                            Timber.e("Search HTTP %d: %s", response.code, body?.take(200))
                            return@withContext null
                        }
                        val searchResponse = json.decodeFromString<GeniusSearchResponse>(body)
                        Timber.d("Got %d hits total", searchResponse.response.hits.size)
                        searchResponse.response.hits.take(10).forEachIndexed { i, hit ->
                            val r = hit.result
                            Timber.d("  Hit %d: \"%s\" — %s (id=%d)", i, r.title, r.artistNames, r.id)
                        }
                        val match = searchResponse.response.hits.take(3).firstOrNull { hit ->
                            val t = hit.result.title
                            t.contains(checkTitle, ignoreCase = true) ||
                            t.normalizeForMatch().contains(checkTitle.normalizeForMatch())
                        }?.result
                        if (match == null) {
                            Timber.d("No hit in top 3 matched \"$checkTitle\"")
                            return@withContext null
                        }
                        Timber.d("Selected: \"%s\" — %s (%s)", match.title, match.artistNames, match.url)
                        match.url
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Search failed for query=\"%s\"", rawQuery)
                    null
                }
            }
        }
    }

    private suspend fun fetchLyricsFromPage(pageUrl: String): String? {
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(pageUrl)
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    client.newCall(request).execute().use { response ->
                        val html = response.body?.string() ?: return@withContext null
                        Timber.d("HTML length: %d", html.length)
                        parseLyricsFromHtml(html)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch lyrics page")
                    null
                }
            }
        }
    }

    private fun parseLyricsFromHtml(html: String): String? {
        val doc = Jsoup.parse(html)
        val containers = doc.select("div[data-lyrics-container=true]")
        containers.forEach { it.select("[data-exclude-from-selection=true]").remove() }

        Timber.d("Found %d lyrics containers", containers.size)
        containers.forEachIndexed { i, el ->
            Timber.d("Container %d: %s", i, el.html().take(500))
        }

        if (containers.isEmpty()) return null

        val raw = containers.joinToString("\n\n") { el ->
            el.html()
                .replace(Regex("\\s*(?i)<br\\s*/?>\\s*<br\\s*/?>\\s*"), "\n\n")
                .replace(Regex("\\s*(?i)<br\\s*/?>\\s*"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&#x27;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }

        val cleaned = raw.lines()
            .map { it.trim() }
            .dropWhile { line ->
                line.contains("Contributors", ignoreCase = true) ||
                line.contains("Lyrics", ignoreCase = true) ||
                line.contains("Translations", ignoreCase = true)
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        Timber.d("Cleaned lyrics (%d chars): %s", cleaned.length, cleaned.take(500))
        return cleaned
    }

    private fun String.normalizeForMatch(): String {
        return java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .lowercase()
            .trim()
    }
}
