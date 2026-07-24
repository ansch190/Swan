package com.schwanitz.data.source

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.schwanitz.data.source.smb.SmbConnectionManager
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val DIR_ATTR = 0x10L

@Singleton
class SmbMusicSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: SmbConnectionManager
) : MusicSource {

    override val type: SourceType = SourceType.SMB

    private companion object {
        const val BATCH_SIZE = 100
        const val CONCURRENCY = 3
        const val DEFAULT_RANGE_LIMIT = 524287L
        const val MP3_HEADER_RANGE_LIMIT = 1023L
        const val MP3_HEADER_SIZE = 10L
        const val MAX_DEPTH = 50
        val RETRY_BACKOFF_MS = longArrayOf(1000, 2000, 4000)
    }

    override suspend fun loadSongs(
        config: SourceConfig,
        onProgress: (Int, Int) -> Unit,
        onBatch: suspend (LoadSongsResult) -> Unit
    ): LoadSongsResult = withContext(Dispatchers.IO) {
        val host = config.url?.trimEnd('/')
            ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())
        val sharePath = config.path?.trim('/')
            ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())
        val shareName = sharePath.substringBefore('/')
        val subPath = sharePath.substringAfter('/', "").let { if (it.isNotEmpty()) "/$it" else "" }
        val username = config.username ?: ""
        val password = config.password ?: ""

        val audioEntries = try {
            collectAudioFiles(host, shareName, subPath, username, password)
        } catch (e: Exception) {
            Timber.e(e, "SMB enumeration failed for %s/%s", host, sharePath)
            emptyList()
        }
        val total = audioEntries.size
        Timber.i("SMB enumeration complete: %d audio files found", total)

        if (audioEntries.isEmpty()) {
            return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())
        }

        val albumArtworkCache = ConcurrentHashMap<String, List<ArtworkResult>>()
        var batchSongs = mutableListOf<Song>()
        var batchAlbums = mutableMapOf<String, Album>()
        var batchArtworks = mutableMapOf<String, MutableList<AlbumArtwork>>()
        val semaphore = Semaphore(CONCURRENCY)

        Timber.i("Starting metadata extraction for %d files (concurrency=%d)", total, CONCURRENCY)
        val failedPaths = mutableSetOf<String>()

        var results = coroutineScope {
            audioEntries.mapIndexed { index, entry ->
                async {
                    semaphore.withPermit {
                        val t0 = System.currentTimeMillis()
                        onProgress(index + 1, total)
                        val result = extractMetadata(entry, config.id, host, username, password, albumArtworkCache)
                        val dt = System.currentTimeMillis() - t0
                        if (result.song == null) failedPaths.add(entry.relativePath)
                        Timber.d("[%d/%d] %s -> %s (%dms)", index + 1, total,
                            entry.relativePath.substringAfterLast('/'),
                            result.song?.title ?: "FAILED", dt)
                        result
                    }
                }
            }.awaitAll()
        }

        if (failedPaths.isNotEmpty()) {
            Timber.i("Retrying %d failed metadata extractions", failedPaths.size)
            val retryResults = coroutineScope {
                audioEntries.filter { it.relativePath in failedPaths }.mapIndexed { index, entry ->
                    async {
                        semaphore.withPermit {
                            val t0 = System.currentTimeMillis()
                            val result = extractMetadata(entry, config.id, host, username, password, albumArtworkCache)
                            val dt = System.currentTimeMillis() - t0
                            Timber.d("[RETRY %d/%d] %s -> %s (%dms)", index + 1, failedPaths.size,
                                entry.relativePath.substringAfterLast('/'),
                                result.song?.title ?: "FAILED", dt)
                            result
                        }
                    }
                }.awaitAll()
            }
            val recovered = retryResults.filter { it.song != null }
            if (recovered.isNotEmpty()) {
                Timber.i("Recovered %d files in retry pass", recovered.size)
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
                    batchAlbums[albumKey] = Album(
                        name = result.song.albumName,
                        albumArtist = result.song.albumArtistName,
                        year = result.song.year
                    )
                }

                val albumForSong = batchAlbums[albumKey]!!
                batchSongs.add(result.song.copy(albumId = albumForSong.id))

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
            onBatch(LoadSongsResult(batchSongs, batchAlbums.values.toList(), batchArtworks.toMap()))
        }

        Timber.i("SMB scan: %d enumerated, %d OK, %d FAILED", total, successCount, failCount)
        LoadSongsResult(emptyList(), emptyList(), emptyMap())
    }

    private suspend fun collectAudioFiles(
        host: String,
        shareName: String,
        subPath: String,
        username: String,
        password: String
    ): List<SmbAudioEntry> = withContext(Dispatchers.IO) {
        val session = connectionManager.connect(host, username, password)
        try {
            val share = session.connectShare(shareName) as DiskShare
            val diskName = share.smbPath.shareName
            val result = mutableListOf<SmbAudioEntry>()
            listRecursive(share, host, diskName, subPath.removePrefix("/"), result, 0)
            result
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }

    private fun listRecursive(
        share: DiskShare,
        host: String,
        diskName: String,
        path: String,
        result: MutableList<SmbAudioEntry>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return

        val entries = try {
            share.list(path)
        } catch (e: Exception) {
            Timber.e(e, "Failed to list %s", path)
            return
        }

        for (entry in entries) {
            val name = entry.fileName
            if (name == "." || name == "..") continue

            val fullPath = if (path.isEmpty()) name else "$path/$name"
            val isDir = (entry.fileAttributes and DIR_ATTR) != 0L

            if (isDir) {
                listRecursive(share, host, diskName, fullPath, result, depth + 1)
            } else if (name.isAudioFile()) {
                val encodedPath = URLEncoder.encode(fullPath, "UTF-8").replace("+", "%20")
                result.add(SmbAudioEntry(
                    smbUri = "smb://$host/$diskName/$encodedPath",
                    relativePath = fullPath,
                    shareName = diskName,
                    fileSize = entry.endOfFile
                ))
            }
        }
    }

    private fun extractMetadata(
        entry: SmbAudioEntry,
        sourceId: String,
        host: String,
        username: String,
        password: String,
        albumArtworkCache: MutableMap<String, List<ArtworkResult>>
    ): BuildSongResult {
        for (attempt in 1..3) {
            try {
                val extension = entry.relativePath.substringAfterLast('.', "").lowercase()
                val rangeLimit = if (extension == "mp3") downloadMp3Range(entry, host, username, password) else DEFAULT_RANGE_LIMIT
                val bytes = downloadBytes(entry, host, username, password, 0, rangeLimit)

                val source = SeekableDataSources.forBytes(bytes)
                try {
                    return MetadataExtractor.buildSong(
                        source, entry.smbUri, sourceId, context, entry.fileSize,
                        albumArtworkCache = albumArtworkCache,
                        fileExtension = extension
                    )
                } finally {
                    source.close()
                }
            } catch (e: Exception) {
                val isRetryable = e is IOException || e is java.net.SocketTimeoutException
                if (attempt < 3 && isRetryable) {
                    Thread.sleep(RETRY_BACKOFF_MS[attempt - 1])
                } else {
                    Timber.e(e, "Metadata extraction failed for %s", entry.relativePath)
                    return BuildSongResult(null, emptyList())
                }
            }
        }
        return BuildSongResult(null, emptyList())
    }

    private fun downloadMp3Range(
        entry: SmbAudioEntry,
        host: String,
        username: String,
        password: String
    ): Long {
        return try {
            val headerBytes = downloadBytes(entry, host, username, password, 0, MP3_HEADER_RANGE_LIMIT)
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
                (MP3_HEADER_SIZE + synchsafeSize).coerceAtMost(1048575L)
            } else {
                262143L
            }
        } catch (e: Exception) {
            Timber.w(e, "MP3 header probe failed for %s", entry.relativePath)
            262143L
        }
    }

    private fun downloadBytes(
        entry: SmbAudioEntry,
        host: String,
        username: String,
        password: String,
        offset: Long,
        length: Long
    ): ByteArray {
        val session = connectionManager.connect(host, username, password)
        try {
            val share = session.connectShare(entry.shareName) as DiskShare
            val file = share.openFile(
                entry.relativePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            try {
                val inputStream = file.getInputStream()
                val bytesToRead = minOf(length, entry.fileSize - offset).toInt()
                val buffer = ByteArray(bytesToRead)
                if (offset > 0) {
                    var skipped = offset
                    val skipBuf = ByteArray(8192)
                    while (skipped > 0) {
                        val toSkip = minOf(skipBuf.size.toLong(), skipped).toInt()
                        val s = inputStream.read(skipBuf, 0, toSkip)
                        if (s <= 0) break
                        skipped -= s
                    }
                }
                var totalRead = 0
                while (totalRead < bytesToRead) {
                    val read = inputStream.read(buffer, totalRead, bytesToRead - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                return buffer.copyOf(totalRead)
            } finally {
                try { file.close() } catch (_: Exception) {}
            }
        } finally {
            try { session.close() } catch (_: Exception) {}
        }
    }

    suspend fun testConnection(
        host: String,
        shareName: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val session = connectionManager.connect(host, username, password)
            try {
                val share = session.connectShare(shareName) as DiskShare
                val entries = share.list("")
                val fileCount = entries.count {
                    val isDir = (it.fileAttributes and DIR_ATTR) != 0L
                    !isDir && it.fileName.isAudioFile()
                }
                Result.success("Verbunden (${entries.size} Einträge, $fileCount Audiodateien)")
            } finally {
                try { session.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            val msg = "${rootCause.javaClass.simpleName}: ${rootCause.message}"
            Timber.e(e, "SMB test failed for %s/%s", host, shareName)
            Result.failure(Exception(msg))
        }
    }

    private data class SmbAudioEntry(
        val smbUri: String,
        val relativePath: String,
        val shareName: String,
        val fileSize: Long
    )
}
