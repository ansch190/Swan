package com.schwanitz.data.lastfm

import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import com.schwanitz.data.rateLimit.RateLimiter
import com.schwanitz.di.LastFmRateLimiter as LastFmQualifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class LastFmApiService @Inject constructor(
    @LastFmQualifier private val rateLimiter: RateLimiter,
    private val client: OkHttpClient
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val apiBase = "https://ws.audioscrobbler.com/2.0/"

    suspend fun getArtistInfo(artistName: String): LastFmArtist? {
        if (BuildConfig.LASTFM_API_KEY.isBlank()) {
            Timber.e("LASTFM_API_KEY is empty - set lastfmKey in local.properties")
            return null
        }

        val url = buildString {
            append(apiBase)
            append("?method=artist.getInfo")
            append("&artist=${java.net.URLEncoder.encode(artistName, "UTF-8")}")
            append("&api_key=${BuildConfig.LASTFM_API_KEY}")
            append("&format=json")
        }

        Timber.d("GET artist.getInfo: %s", artistName)
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    rateLimiter.acquire()
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string()
                        if (body == null || !response.isSuccessful) {
                            Timber.e("HTTP %d for %s: %s", response.code, artistName, body?.take(200))
                            return@withContext null
                        }
                        val envelope = json.decodeFromString<LastFmArtistResponse>(body)
                        envelope.artist
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Request failed for %s", artistName)
                    null
                }
            }
        }
    }
}
