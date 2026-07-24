package com.schwanitz.data.source

import android.content.Context
import com.schwanitz.R
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.source.LoadSongsResult
import com.schwanitz.domain.source.MusicSource
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import com.schwanitz.io.SeekableDataSources
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavMusicSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) : MusicSource {

    override val type: SourceType = SourceType.WEBDAV

    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()

    private val propfindBody = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:propfind xmlns:D="DAV:">
          <D:allprop/>
        </D:propfind>
    """.trimIndent().toRequestBody(xmlMediaType)

    private companion object {
        const val BATCH_SIZE = 100
        const val CONCURRENCY = 3
        const val DEFAULT_RANGE_LIMIT = 524287L
        const val MP3_HEADER_RANGE_LIMIT = 1023L
        const val MP3_HEADER_SIZE = 10
        const val MAX_DEPTH = 50
        const val PROPFIND_MAX_RETRIES = 3
        val PROPFIND_BACKOFF_MS = longArrayOf(1000, 2000, 4000)
    }

    private val propfindClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun loadSongs(
        config: SourceConfig,
        onProgress: (Int, Int) -> Unit,
        onBatch: suspend (LoadSongsResult) -> Unit
    ): LoadSongsResult = withContext(Dispatchers.IO) {
        val baseUrl = config.url?.trimEnd('/') ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())
        val username = config.username
        val password = config.password

        val rawPath = config.path?.let { "/${it.trimStart('/')}" } ?: "/"
        val startUrl = if (rawPath.startsWith("http")) rawPath else "$baseUrl$rawPath"

        val audioEntries = collectAudioFiles(baseUrl, username, password, startUrl)
        val total = audioEntries.size
        Timber.e("WebDAV enumeration complete: %d audio files found", total)

        if (audioEntries.isEmpty()) {
            return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())
        }

        val albumArtworkCache = ConcurrentHashMap<String, List<ArtworkResult>>()
        var batchSongs = mutableListOf<Song>()
        var batchAlbums = mutableMapOf<String, Album>()
        var batchArtworks = mutableMapOf<String, MutableList<AlbumArtwork>>()
        val semaphore = Semaphore(CONCURRENCY)

        Timber.e("Starting metadata extraction for %d files (concurrency=%d)", total, CONCURRENCY)
        val failedUrls = mutableSetOf<String>()

        var results = coroutineScope {
            audioEntries.mapIndexed { index, (url, fileSize) ->
                async {
                    semaphore.withPermit {
                        val t0 = System.currentTimeMillis()
                        onProgress(index + 1, total)
                        val result = extractMetadata(url, config.id, username, password, fileSize, albumArtworkCache)
                        val dt = System.currentTimeMillis() - t0
                        if (result.song == null) failedUrls.add(url)
                        Timber.d("[%d/%d] %s -> %s (%dms)", index + 1, total,
                            url.substringAfterLast('/'),
                            result.song?.title ?: "FAILED", dt)
                        result
                    }
                }
            }.awaitAll()
        }

        if (failedUrls.isNotEmpty()) {
            Timber.e("Metadata extraction failed for %d/%d files, starting retry pass", failedUrls.size, total)
            val retryResults = coroutineScope {
                audioEntries.filter { (url, _) -> url in failedUrls }.mapIndexed { index, (url, fileSize) ->
                    async {
                        semaphore.withPermit {
                            val t0 = System.currentTimeMillis()
                            val result = extractMetadata(url, config.id, username, password, fileSize, albumArtworkCache)
                            val dt = System.currentTimeMillis() - t0
                            Timber.d("[RETRY %d/%d] %s -> %s (%dms)", index + 1, failedUrls.size,
                                url.substringAfterLast('/'),
                                result.song?.title ?: "FAILED", dt)
                            result
                        }
                    }
                }.awaitAll()
            }
            val recovered = retryResults.filter { it.song != null }
            if (recovered.isNotEmpty()) {
                Timber.e("Recovered %d files in retry pass", recovered.size)
            }
            results = results.filter { it.song != null } + retryResults
        }

        var successCount = 0
        var failCount = 0
        for (result in results) {
            if (result.song != null) {
                successCount++
                val albumKey = "${result.song.albumArtistName}|${result.song.albumName}"
                if (albumKey !in batchAlbums) {
                    val albumEntry = Album(
                        name = result.song.albumName,
                        albumArtist = result.song.albumArtistName,
                        year = result.song.year
                    )
                    batchAlbums[albumKey] = albumEntry
                }

                val albumForSong = batchAlbums[albumKey]!!
                val songWithAlbumId = result.song.copy(albumId = albumForSong.id)
                batchSongs.add(songWithAlbumId)

                if (result.artworks.isNotEmpty() && albumKey !in batchArtworks) {
                    val artworkList = batchArtworks.getOrPut(albumKey) { mutableListOf() }
                    for (artResult in result.artworks) {
                        artworkList.add(
                            AlbumArtwork(
                                albumId = 0,
                                sortOrder = artworkList.size,
                                uriLarge = artResult.largeUri,
                                uriSmall = artResult.smallUri
                            )
                        )
                    }
                }

                if (successCount % BATCH_SIZE == 0 && batchSongs.isNotEmpty()) {
                    Timber.d("Flushing batch of %d songs", batchSongs.size)
                    onBatch(LoadSongsResult(batchSongs.toList(), batchAlbums.values.toList(), batchArtworks.toMap()))
                    batchSongs = mutableListOf()
                    batchAlbums = mutableMapOf()
                    batchArtworks = mutableMapOf()
                }
            } else {
                failCount++
            }
        }

        if (batchSongs.isNotEmpty()) {
            Timber.d("Flushing final batch of %d songs", batchSongs.size)
            onBatch(LoadSongsResult(batchSongs, batchAlbums.values.toList(), batchArtworks.toMap()))
        }

        if (failCount > 0) {
            Timber.e("WebDAV scan: %d files enumerated, %d songs OK, %d FAILED", total, successCount, failCount)
        } else {
            Timber.e("WebDAV scan: %d files enumerated, %d songs OK", total, successCount)
        }

        LoadSongsResult(emptyList(), emptyList(), emptyMap())
    }

    private suspend fun collectAudioFiles(
        baseUrl: String,
        username: String?,
        password: String?,
        path: String
    ): List<Pair<String, Long>> = coroutineScope {
        val failedPaths = mutableListOf<String>()

        val firstPass = propfindRecursive(baseUrl, username, password, path, failedPaths, this@coroutineScope, 0)

        if (failedPaths.isNotEmpty()) {
            Timber.e("Retrying %d failed PROPFIND paths", failedPaths.size)
            val retryPaths = failedPaths.toList()
            failedPaths.clear()

            val retryResults = retryPaths.map { failedPath ->
                async { propfindRecursive(baseUrl, username, password, failedPath, failedPaths, this@coroutineScope, 0) }
            }.awaitAll()

            val retryFiles = retryResults.flatten()
            if (retryFiles.isNotEmpty()) {
                Timber.e("Recovered %d files from retry pass", retryFiles.size)
            }
            for (p in failedPaths) {
                Timber.e("PROPFIND permanently failed for %s — files in this subtree are missing", p)
            }
            firstPass + retryFiles
        } else {
            firstPass
        }
    }

    private suspend fun propfindRecursive(
        baseUrl: String,
        username: String?,
        password: String?,
        path: String,
        failedPaths: MutableList<String>,
        scope: CoroutineScope,
        depth: Int
    ): List<Pair<String, Long>> {
        if (depth > MAX_DEPTH) {
            Timber.e("Max recursion depth (%d) reached for %s", MAX_DEPTH, path)
            return emptyList()
        }

        val entries = try {
            propfind(baseUrl, username, password, path)
        } catch (e: Exception) {
            Timber.e(e, "PROPFIND failed for %s after %d retries, adding to retry queue", path, PROPFIND_MAX_RETRIES)
            failedPaths.add(path)
            return emptyList()
        }

        val audioFiles = mutableListOf<Pair<String, Long>>()
        val childDirs = mutableListOf<String>()

        for (entry in entries) {
            val fullUrl = if (entry.href.startsWith("http")) {
                entry.href
            } else {
                val baseUri = java.net.URI(baseUrl)
                val portStr = if (baseUri.port != -1) ":${baseUri.port}" else ""
                "${baseUri.scheme}://${baseUri.host}$portStr/${entry.href.trimStart('/')}"
            }

            if (entry.isCollection) {
                val decodedHref = try {
                    URLDecoder.decode(entry.href, "UTF-8")
                } catch (_: Exception) {
                    entry.href
                }
                val decodedPath = try {
                    URLDecoder.decode(path, "UTF-8")
                } catch (_: Exception) {
                    path
                }
                val entryPath = decodedHref.trim('/').lowercase()
                val currentPathOnly = decodedPath.toHttpUrlOrNull()?.encodedPath ?: decodedPath
                val currentPath = currentPathOnly.trim('/').lowercase()

                if (entryPath != currentPath && entryPath.isNotEmpty()) {
                    if (entryPath.startsWith(currentPath) || currentPath.isEmpty()) {
                        childDirs.add(entry.href)
                    }
                }
            } else if (entry.href.isAudioFile()) {
                audioFiles.add(fullUrl to entry.contentLength)
            }
        }

        val childResults = childDirs.map { dir ->
            scope.async {
                try {
                    propfindRecursive(baseUrl, username, password, dir, failedPaths, scope, depth + 1)
                } catch (e: Exception) {
                    Timber.e(e, "Child traversal failed for %s, skipping subtree", dir)
                    failedPaths.add(dir)
                    emptyList()
                }
            }
        }.awaitAll()

        return audioFiles + childResults.flatten()
    }

    private fun propfind(
        baseUrl: String,
        username: String?,
        password: String?,
        path: String
    ): List<WebDavEntry> {
        val url = if (path.startsWith("http")) {
            path
        } else {
            val base = baseUrl.trimEnd('/')
            val p = if (path.startsWith("/")) path else "/$path"
            "$base$p"
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .method("PROPFIND", propfindBody)
            .header("Depth", "1")

        if (username != null && password != null) {
            requestBuilder.header("Authorization", Credentials.basic(username, password))
        }

        val request = requestBuilder.build()
        var lastException: Exception? = null

        for (attempt in 1..PROPFIND_MAX_RETRIES) {
            try {
                propfindClient.newCall(request).execute().use { response ->
                    if (response.code == 401) {
                        Timber.e("Authentication failed for %s", url)
                        return emptyList()
                    }

                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        throw IOException("HTTP ${response.code} from $url")
                    }

                    return parsePropfindResponse(responseBody, url)
                }
            } catch (e: Exception) {
                lastException = e
                val isRetryable = e is IOException || e is java.net.SocketTimeoutException
                if (attempt < PROPFIND_MAX_RETRIES && isRetryable) {
                    val waitMs = PROPFIND_BACKOFF_MS[attempt - 1]
                    Timber.w(e, "PROPFIND retry %d/%d for %s in %dms", attempt, PROPFIND_MAX_RETRIES, url, waitMs)
                    Thread.sleep(waitMs)
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IOException("PROPFIND failed for $url after $PROPFIND_MAX_RETRIES attempts")
    }

    private fun parsePropfindResponse(
        xml: String,
        requestUrl: String
    ): List<WebDavEntry> {
        val entries = mutableListOf<WebDavEntry>()

        val normalizedRequestPath = requestUrl.toHttpUrlOrNull()?.encodedPath?.trim('/')?.lowercase() ?: ""

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var currentHref: String? = null
            var isCollection = false
            var currentContentLength = 0L

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                val eventType = parser.eventType
                val name = parser.name?.lowercase()

                if (eventType == XmlPullParser.START_TAG) {
                    when {
                        name?.endsWith("href") == true -> {
                            currentHref = parser.nextText().trim()
                        }
                        name?.endsWith("collection") == true -> {
                            isCollection = true
                        }
                        name?.endsWith("getcontentlength") == true -> {
                            currentContentLength = parser.nextText().toLongOrNull() ?: 0L
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (name?.endsWith("response") == true) {
                        if (currentHref != null) {
                            val entryEncodedPath = currentHref.toHttpUrlOrNull()?.encodedPath ?: currentHref
                            val normalizedEntryPath = entryEncodedPath.trim('/').lowercase()

                            if (normalizedEntryPath != normalizedRequestPath) {
                                entries.add(WebDavEntry(currentHref, isCollection, currentContentLength))
                            }
                        }
                        currentHref = null
                        isCollection = false
                        currentContentLength = 0L
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PROPFIND response from %s", requestUrl)
        }

        return entries
    }

    private fun extractMetadata(
        audioUrl: String,
        sourceId: String,
        username: String?,
        password: String?,
        fileSize: Long = 0L,
        albumArtworkCache: MutableMap<String, List<ArtworkResult>>
    ): BuildSongResult {
        val extension = audioUrl.substringAfterLast('.', "").lowercase()
        val maxRetries = 3
        val backoffMs = longArrayOf(1000, 2000, 4000)

        for (attempt in 1..maxRetries) {
            try {
                val rangeHeader = getRequestRange(audioUrl, username, password, fileSize, extension)
                val t0 = System.currentTimeMillis()
                val bytes = downloadToBytes(audioUrl, username, password, rangeHeader)
                val dlDt = System.currentTimeMillis() - t0
                Timber.d("Downloaded %d bytes in %dms for %s (range: %s)", bytes.size, dlDt, audioUrl, rangeHeader)

                val source = SeekableDataSources.forBytes(bytes)
                try {
                    return MetadataExtractor.buildSong(
                        source, audioUrl, sourceId, context, fileSize,
                        albumArtworkCache = albumArtworkCache,
                        fileExtension = extension
                    )
                } finally {
                    source.close()
                }
            } catch (e: Exception) {
                val isRetryable = e is java.io.IOException || e is java.net.SocketTimeoutException
                if (attempt < maxRetries && isRetryable) {
                    val waitMs = backoffMs[attempt - 1]
                    Timber.w(e, "Retry %d/%d for %s in %dms", attempt, maxRetries, audioUrl, waitMs)
                    Thread.sleep(waitMs)
                } else {
                    Timber.e(e, "Metadata extraction failed for %s (attempt %d/%d)", audioUrl, attempt, maxRetries)
                    return BuildSongResult(null, emptyList())
                }
            }
        }
        return BuildSongResult(null, emptyList())
    }

    private fun getRequestRange(
        url: String,
        username: String?,
        password: String?,
        fileSize: Long,
        extension: String
    ): String {
        if (extension == "mp3") {
            return try {
                val headerBytes = downloadToBytes(url, username, password, "bytes=0-$MP3_HEADER_RANGE_LIMIT")
                if (headerBytes.size >= MP3_HEADER_SIZE &&
                    headerBytes[0] == 0x49.toByte() &&
                    headerBytes[1] == 0x44.toByte() &&
                    headerBytes[2] == 0x33.toByte()
                ) {
                    val synchsafeSize =
                        (headerBytes[6].toInt() and 0x7F shl 21) or
                        (headerBytes[7].toInt() and 0x7F shl 14) or
                        (headerBytes[8].toInt() and 0x7F shl 7) or
                        (headerBytes[9].toInt() and 0x7F)
                    val totalTagSize = MP3_HEADER_SIZE + synchsafeSize
                    val rangeEnd = totalTagSize.coerceAtMost(1048575) - 1
                    Timber.d("MP3 ID3v2 tag detected: %d bytes (range: 0-%d)", totalTagSize, rangeEnd)
                    "bytes=0-$rangeEnd"
                } else {
                    Timber.d("No ID3v2 header found for %s, using default range", url)
                    "bytes=0-262143"
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to probe MP3 header for %s, using default range", url)
                "bytes=0-262143"
            }
        }
        return "bytes=0-$DEFAULT_RANGE_LIMIT"
    }

    private fun downloadToBytes(
        url: String,
        username: String?,
        password: String?,
        rangeHeader: String? = null
    ): ByteArray {
        val requestBuilder = Request.Builder().url(url)
        if (rangeHeader != null) {
            requestBuilder.header("Range", rangeHeader)
        }
        if (username != null && password != null) {
            requestBuilder.header("Authorization", Credentials.basic(username, password))
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response for $url")
            return response.body?.bytes() ?: throw IOException("Empty body for $url")
        }
    }

    suspend fun testConnection(
        url: String,
        username: String,
        password: String,
        path: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val entries = propfind(url, username, password, path.ifBlank { "/" })
            if (entries.isEmpty()) {
                Result.failure(Exception(context.getString(R.string.add_source_webdav_test_failed)))
            } else {
                Result.success(context.getString(R.string.add_source_webdav_connected, entries.size))
            }
        } catch (e: Exception) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            val msg = "${rootCause.javaClass.simpleName}: ${rootCause.message}"
            Timber.e(e, "WebDAV test failed for %s", url)
            Result.failure(Exception(msg))
        }
    }

    private data class WebDavEntry(
        val href: String,
        val isCollection: Boolean,
        val contentLength: Long = 0L
    )
}
