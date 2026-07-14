package com.schwanitz.data.discogs

import com.schwanitz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.schwanitz.data.rateLimit.RateLimiter
import com.schwanitz.di.DiscogsRateLimiter as DiscogsQualifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class DiscogsApiService @Inject constructor(
    @DiscogsQualifier private val rateLimiter: RateLimiter
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
        Timber.d("downloadImage: %s", imageUrl)
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(imageUrl).build()
                    Timber.d("execute() enter: downloadImage")
                    client.newCall(request).execute().use { response ->
                        Timber.d("execute() exit: downloadImage, code=%d", response.code)
                        val bytes = response.body?.bytes()
                        Timber.d("downloadImage response: code=%d, bytes=%d", response.code, bytes?.size)
                        bytes
                    }
                } catch (e: Exception) {
                    Timber.e(e, "downloadImage failed")
                    null
                }
            }
        }
    }

    private suspend inline fun <reified T> get(url: String, tag: String = ""): T? {
        rateLimiter.acquire()
        Timber.d("GET %s: %s", tag, url)
        return withTimeout(30_000.milliseconds) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "SwanMusicPlayer/1.0")
                        .build()
                    Timber.d("execute() enter: %s", tag)
                    client.newCall(request).execute().use { response ->
                        Timber.d("execute() exit: %s, code=%d", tag, response.code)
                        val body = response.body?.string()
                        Timber.d("Response %s: code=%d, body=%s", tag, response.code, body?.take(200))
                        if (body == null) {
                            Timber.e("Response body was null for %s", tag)
                            return@withContext null
                        }
                        if (!response.isSuccessful) {
                            Timber.e("HTTP %d for %s: %s", response.code, tag, body)
                            return@withContext null
                        }
                        json.decodeFromString<T>(body)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Request failed for %s", tag)
                    null
                }
            }
        }
    }
}
