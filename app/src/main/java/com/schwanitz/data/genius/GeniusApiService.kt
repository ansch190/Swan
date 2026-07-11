package com.schwanitz.data.genius

import android.util.Log
import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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
            Log.e("GeniusAPI", "GENIUS_ACCESS_TOKEN is empty")
            return null
        }

        val query1 = "$title - $artist"
        Log.e("GeniusAPI", "Search attempt 1: \"$query1\"")
        val url1 = searchForSongOrNull(query1, title)
        if (url1 != null) return fetchLyricsFromPage(url1)

        val cleanTitle = title.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        if (cleanTitle != title) {
            val query2 = "$cleanTitle - $artist"
            Log.e("GeniusAPI", "Search attempt 2: \"$query2\"")
            val url2 = searchForSongOrNull(query2, cleanTitle)
            if (url2 != null) return fetchLyricsFromPage(url2)
        }

        Log.e("GeniusAPI", "No matching song found for \"$title\" - $artist")
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
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (body == null || !response.isSuccessful) {
                        Log.e("GeniusAPI", "Search HTTP ${response.code}: ${body?.take(200)}")
                        return@withContext null
                    }
                    val searchResponse = json.decodeFromString<GeniusSearchResponse>(body)
                    Log.e("GeniusAPI", "Got ${searchResponse.response.hits.size} hits total")
                    searchResponse.response.hits.take(10).forEachIndexed { i, hit ->
                        val r = hit.result
                        Log.e("GeniusAPI", "  Hit $i: \"${r.title}\" — ${r.artistNames} (id=${r.id})")
                    }
                    val match = searchResponse.response.hits.take(3).firstOrNull { hit ->
                        val t = hit.result.title
                        t.contains(checkTitle, ignoreCase = true) ||
                        t.normalizeForMatch().contains(checkTitle.normalizeForMatch())
                    }?.result
                    if (match == null) {
                        Log.e("GeniusAPI", "No hit in top 3 matched \"$checkTitle\"")
                        return@withContext null
                    }
                    Log.e("GeniusAPI", "Selected: \"${match.title}\" — ${match.artistNames} (${match.url})")
                    match.url
                } catch (e: Exception) {
                    Log.e("GeniusAPI", "Search failed for query=\"$rawQuery\"", e)
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
                    val response = client.newCall(request).execute()
                    val html = response.body?.string() ?: return@withContext null
                    Log.e("GeniusAPI", "HTML length: ${html.length}")
                    parseLyricsFromHtml(html)
                } catch (e: Exception) {
                    Log.e("GeniusAPI", "Failed to fetch lyrics page", e)
                    null
                }
            }
        }
    }

    private fun parseLyricsFromHtml(html: String): String? {
        val doc = Jsoup.parse(html)
        val containers = doc.select("div[data-lyrics-container=true]")
        containers.forEach { it.select("[data-exclude-from-selection=true]").remove() }

        Log.e("GeniusAPI", "Found ${containers.size} lyrics containers")
        containers.forEachIndexed { i, el ->
            Log.e("GeniusAPI", "Container $i: ${el.html().take(500)}")
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

        Log.e("GeniusAPI", "Cleaned lyrics (${cleaned.length} chars): ${cleaned.take(500)}")
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
