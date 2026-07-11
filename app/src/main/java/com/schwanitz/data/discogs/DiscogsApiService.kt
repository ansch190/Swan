package com.schwanitz.data.discogs

import android.util.Log
import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class DiscogsApiService @Inject constructor(
    private val rateLimiter: DiscogsRateLimiter
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val dnsExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "DiscogsDns").also { it.isDaemon = true }
    }

    private val timeoutDns = object : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val future = dnsExecutor.submit(Callable {
                Dns.SYSTEM.lookup(hostname)
            })
            try {
                return future.get(10, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                future.cancel(true)
                throw UnknownHostException("DNS lookup timed out for $hostname")
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(timeoutDns)
        .build()

    private val apiBase = "https://api.discogs.com"
    private val authParams = "key=${BuildConfig.DISCOGS_CONSUMER_KEY}&secret=${BuildConfig.DISCOGS_CONSUMER_SECRET}"

    suspend fun searchArtist(name: String): DiscogsSearchResponse? {
        val url = "$apiBase/database/search?type=artist&q=${java.net.URLEncoder.encode(name, "UTF-8")}&$authParams"
        return get(url, "searchArtist($name)")
    }

    suspend fun getArtistDetail(artistId: Long): DiscogsArtistResponse? {
        val url = "$apiBase/artists/$artistId?$authParams"
        return get(url, "getArtistDetail($artistId)")
    }

    suspend fun downloadImage(imageUrl: String): ByteArray? {
        rateLimiter.acquire()
        Log.e("DiscogsAPI", "downloadImage: $imageUrl")
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(imageUrl).build()
                    Log.e("DiscogsAPI", "execute() enter: downloadImage")
                    val response = client.newCall(request).execute()
                    Log.e("DiscogsAPI", "execute() exit: downloadImage, code=${response.code}")
                    val bytes = response.body?.bytes()
                    Log.e("DiscogsAPI", "downloadImage response: code=${response.code}, bytes=${bytes?.size}")
                    bytes
                } catch (e: Exception) {
                    Log.e("DiscogsAPI", "downloadImage failed", e)
                    null
                }
            }
        }
    }

    private suspend inline fun <reified T> get(url: String, tag: String = ""): T? {
        rateLimiter.acquire()
        Log.e("DiscogsAPI", "GET $tag: $url")
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    Log.e("DiscogsAPI", "execute() enter: $tag")
                    val response = client.newCall(request).execute()
                    Log.e("DiscogsAPI", "execute() exit: $tag, code=${response.code}")
                    val body = response.body?.string()
                    Log.e("DiscogsAPI", "Response $tag: code=${response.code}, body=${body?.take(200)}")
                    if (body == null) {
                        Log.e("DiscogsAPI", "Response body was null for $tag")
                        return@withContext null
                    }
                    if (!response.isSuccessful) {
                        Log.e("DiscogsAPI", "HTTP ${response.code} for $tag: $body")
                        return@withContext null
                    }
                    json.decodeFromString<T>(body)
                } catch (e: Exception) {
                    Log.e("DiscogsAPI", "Request failed for $tag", e)
                    null
                }
            }
        }
    }
}
