package com.schwanitz.data.lastfm

import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class LastFmApiService @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (body == null || !response.isSuccessful) {
                        Timber.e("HTTP %d for %s: %s", response.code, artistName, body?.take(200))
                        return@withContext null
                    }
                    val envelope = json.decodeFromString<LastFmArtistResponse>(body)
                    envelope.artist
                } catch (e: Exception) {
                    Timber.e(e, "Request failed for %s", artistName)
                    null
                }
            }
        }
    }
}
