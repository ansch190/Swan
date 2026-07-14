package com.schwanitz.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.source.LoadSongsResult
import com.schwanitz.domain.source.MusicSource
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import com.schwanitz.io.SeekableDataSources
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.util.UUID
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

    override suspend fun loadSongs(
        config: SourceConfig,
        onProgress: (Int, Int) -> Unit
    ): LoadSongsResult = withContext(Dispatchers.IO) {
        try {
            val baseUrl = config.url?.trimEnd('/') ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())
            val username = config.username
            val password = config.password

            val rawPath = config.path?.let { "/${it.trimStart('/')}" } ?: "/"
            val startUrl = if (rawPath.startsWith("http")) rawPath else "$baseUrl$rawPath"

            val audioEntries = mutableListOf<Pair<String, Long>>()
            collectAudioFiles(baseUrl, username, password, startUrl, audioEntries)
            val total = audioEntries.size
            Timber.d("Finished collecting. Found %d audio files.", total)

            val songs = mutableListOf<Song>()
            val albumMap = mutableMapOf<String, Album>()
            val albumArtworkMap = mutableMapOf<String, MutableList<AlbumArtwork>>()
            val albumArtworkCache = mutableMapOf<String, List<String>>()

            Timber.d("Starting metadata extraction for %d files", total)
            audioEntries.forEachIndexed { index, (url, fileSize) ->
                val t0 = System.currentTimeMillis()
                onProgress(index + 1, total)
                val result = extractMetadata(url, config.id, username, password, fileSize, albumArtworkCache)
                val dt = System.currentTimeMillis() - t0
                if (result.song != null) {
                    Timber.d("[%d/%d] '%s' OK (%dms)", index + 1, total, result.song.title, dt)

                    val albumKey = "${result.song.albumArtistName}|${result.song.albumName}|${result.song.year}"
                    if (albumKey !in albumMap) {
                        val albumEntry = Album(
                            name = result.song.albumName,
                            albumArtist = result.song.albumArtistName,
                            year = result.song.year
                        )
                        albumMap[albumKey] = albumEntry
                    }

                    val albumForSong = albumMap[albumKey]!!
                    val songWithAlbumId = result.song.copy(albumId = albumForSong.id)
                    songs.add(songWithAlbumId)

                    if (result.artworks.isNotEmpty()) {
                        val artworkList = albumArtworkMap.getOrPut(albumKey) { mutableListOf() }
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
                } else {
                    Timber.w("[%d/%d] %s -> FAILED (%dms)", index + 1, total, url.substringAfterLast('/'), dt)
                }
            }

            val albums = albumMap.values.toList()
            val allArtworks = albumArtworkMap.toMap()
            LoadSongsResult(songs, albums, allArtworks)
        } catch (e: Exception) {
            Timber.e(e, "loadSongs failed for %s", config.url)
            LoadSongsResult(emptyList(), emptyList(), emptyMap())
        }
    }

    private fun collectAudioFiles(
        baseUrl: String,
        username: String?,
        password: String?,
        path: String,
        result: MutableList<Pair<String, Long>>,
        depth: Int = 0
    ) {
        if (depth > 10) {
            Timber.w("Max recursion depth reached for %s", path)
            return
        }

        val entries = propfind(baseUrl, username, password, path)
        for (entry in entries) {
            val fullUrl = if (entry.href.startsWith("http")) {
                entry.href
            } else {
                val baseUri = java.net.URI(baseUrl)
                val portStr = if (baseUri.port != -1) ":${baseUri.port}" else ""
                "${baseUri.scheme}://${baseUri.host}$portStr/${entry.href.trimStart('/')}"
            }

            if (entry.isCollection) {
                val entryPath = entry.href.trim('/').lowercase()
                val currentPathOnly = path.toHttpUrlOrNull()?.encodedPath ?: path
                val currentPath = currentPathOnly.trim('/').lowercase()

                if (entryPath != currentPath && entryPath.isNotEmpty()) {
                    if (entryPath.startsWith(currentPath) || currentPath.isEmpty()) {
                        collectAudioFiles(baseUrl, username, password, entry.href, result, depth + 1)
                    }
                }
            } else if (entry.href.isAudioFile()) {
                result.add(fullUrl to entry.contentLength)
            }
        }
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

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 401) {
                    Timber.e("Authentication failed for %s", url)
                    return emptyList()
                }

                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    Timber.w("HTTP %d from %s", response.code, url)
                    return emptyList()
                }

                parsePropfindResponse(responseBody, url)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PROPFIND response from %s", url)
            emptyList()
        }
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
        albumArtworkCache: MutableMap<String, List<String>>
    ): BuildSongResult {
        val partialFile = try {
            val t0 = System.currentTimeMillis()
            val file = downloadPartial(audioUrl, username, password)
            val dlDt = System.currentTimeMillis() - t0
            Timber.d("Partial download OK: %d bytes in %dms for %s", file.length(), dlDt, audioUrl)
            file
        } catch (e: Exception) {
            Timber.e(e, "Partial download failed for %s", audioUrl)
            return BuildSongResult(null, emptyList())
        }

        try {
            val source = SeekableDataSources.forPath(partialFile.toPath())
            val retriever = MediaMetadataRetriever()
            try {
                Timber.d("Extracting metadata from partial file: %s", partialFile.absolutePath)
                retriever.setDataSource(partialFile.absolutePath)
                return MetadataExtractor.buildSong(
                    source, retriever, audioUrl, sourceId, context, fileSize,
                    albumArtworkCache = albumArtworkCache
                )
            } finally {
                try { retriever.release() } catch (_: Exception) {}
                try { source.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Timber.e(e, "Metadata extraction failed for %s", audioUrl)
            return BuildSongResult(null, emptyList())
        } finally {
            if (partialFile.exists()) partialFile.delete()
        }
    }

    private fun downloadPartial(
        url: String,
        username: String?,
        password: String?
    ): java.io.File {
        val tempFile = java.io.File(context.cacheDir, "webdav_temp_${UUID.randomUUID()}.tmp")

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1048575")

        if (username != null && password != null) {
            requestBuilder.header("Authorization", Credentials.basic(username, password))
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response for $url")
            val contentLength = response.body?.contentLength() ?: -1L
            Timber.d("downloadPartial %s -> %d, content-length=%d", url, response.code, contentLength)
            response.body?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Empty body")
        }
        return tempFile
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
                Result.failure(Exception("Could not connect — check URL and credentials"))
            } else {
                Result.success("Connected successfully (${entries.size} items found)")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class WebDavEntry(
        val href: String,
        val isCollection: Boolean,
        val contentLength: Long = 0L
    )
}
