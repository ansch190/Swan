package com.schwanitz.data.lastfm

import android.util.Log
import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

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
            Log.e("LastFmAPI", "LASTFM_API_KEY is empty - set lastfmKey in local.properties")
            return null
        }

        val url = buildString {
            append(apiBase)
            append("?method=artist.getInfo")
            append("&artist=${java.net.URLEncoder.encode(artistName, "UTF-8")}")
            append("&api_key=${BuildConfig.LASTFM_API_KEY}")
            append("&format=json")
        }

        Log.e("LastFmAPI", "GET artist.getInfo: $artistName")
        return withTimeout(30_000) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    if (body == null || !response.isSuccessful) {
                        Log.e("LastFmAPI", "HTTP ${response.code} for $artistName: ${body?.take(200)}")
                        return@withContext null
                    }
                    val envelope = json.decodeFromString<LastFmArtistResponse>(body)
                    envelope.artist
                } catch (e: Exception) {
                    Log.e("LastFmAPI", "Request failed for $artistName", e)
                    null
                }
            }
        }
    }
}
