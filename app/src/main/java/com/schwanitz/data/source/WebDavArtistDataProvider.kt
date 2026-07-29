package com.schwanitz.data.source

import android.net.Uri
import com.schwanitz.data.local.ArtistDataSourcePreferences
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceType
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavArtistDataProvider @Inject constructor(
    private val client: OkHttpClient,
    private val sourceManager: SourceManager,
    private val prefs: ArtistDataSourcePreferences
) {
    private val httpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun isConfigured(): Boolean {
        return prefs.getSourceIdSync() != null
    }

    suspend fun fetchBio(artistName: String, langCode: String): String? {
        val config = getSourceConfig() ?: return null
        val basePath = prefs.getBasePathSync()
        val filename = "bio_$langCode.txt"
        val url = buildUrl(config.url!!, basePath, artistName, "info", filename)
            ?: return null
        return httpGetString(url, config.username, config.password)
    }

    suspend fun fetchImage(artistName: String): ByteArray? {
        val config = getSourceConfig() ?: return null
        val basePath = prefs.getBasePathSync()
        val url = buildUrl(config.url!!, basePath, artistName, "info", "pic.jpg")
            ?: return null
        return httpGetBytes(url, config.username, config.password)
    }

    private suspend fun getSourceConfig() = runCatching {
        val sourceId = prefs.getSourceIdSync() ?: return null
        val config = sourceManager.getSourceById(sourceId) ?: return null
        if (config.type != SourceType.WEBDAV || config.url.isNullOrBlank()) return null
        config
    }.getOrNull()

    private fun buildUrl(serverUrl: String, basePath: String, artistName: String, vararg segments: String): String? {
        val encodedName = Uri.encode(artistName)
        val path = listOf(basePath.trim('/'), encodedName, *segments)
            .joinToString("/")
        return "${serverUrl.trimEnd('/')}/$path"
    }

    private suspend fun httpGetString(url: String, username: String?, password: String?): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = buildRequest(url, username, password)
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.d("WebDAV artist data: GET %s -> %d", url, response.code)
                    return@withContext null
                }
                response.body?.string()
            }.onFailure { e ->
                Timber.d(e, "WebDAV artist data: GET %s failed", url)
            }.getOrNull()
        }
    }

    private suspend fun httpGetBytes(url: String, username: String?, password: String?): ByteArray? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = buildRequest(url, username, password)
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Timber.d("WebDAV artist data: GET %s -> %d", url, response.code)
                    return@withContext null
                }
                response.body?.bytes()
            }.onFailure { e ->
                Timber.d(e, "WebDAV artist data: GET %s failed", url)
            }.getOrNull()
        }
    }

    private fun buildRequest(url: String, username: String?, password: String?): Request {
        val builder = Request.Builder().url(url)
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            builder.addHeader(
                "Authorization",
                okhttp3.Credentials.basic(username, password)
            )
        }
        return builder.build()
    }
}
