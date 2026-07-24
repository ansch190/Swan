package com.schwanitz.data.source

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.schwanitz.R
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
        const val MP3_HEADER_SIZE = 10L
        const val MAX_DEPTH = 50
        const val MAX_ENUM_RETRIES = 2
        const val LIST_MAX_RETRIES = 3
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
            connectionManager.closeAll()
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

        connectionManager.closeAll()
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
        val share = connectionManager.getShare(session, shareName)
        val diskName = share.smbPath.shareName
        val result = mutableListOf<SmbAudioEntry>()

        var failedDirs = mutableSetOf<String>()
        coroutineScope {
            listRecursive(share, host, diskName, subPath.removePrefix("/"), result, 0, failedDirs, this)
        }

        for (retryPass in 1..MAX_ENUM_RETRIES) {
            if (failedDirs.isEmpty()) break
            Timber.i("Retrying %d failed directories (pass %d/%d)", failedDirs.size, retryPass, MAX_ENUM_RETRIES)
            val retryFailedDirs = mutableSetOf<String>()
            for (dir in failedDirs) {
                try {
                    val s = connectionManager.reconnect(host, username, password)
                    val sh = connectionManager.getShare(s, shareName)
                    coroutineScope {
                        listRecursive(sh, host, diskName, dir, result, 0, retryFailedDirs, this)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Directory retry failed for %s", dir)
                    retryFailedDirs.add(dir)
                }
            }
            if (retryFailedDirs.isEmpty()) {
                Timber.i("All directory retries recovered")
            } else {
                Timber.w("%d directories still failing after retry", retryFailedDirs.size)
            }
            failedDirs = retryFailedDirs
        }

        result
    }

    private suspend fun listRecursive(
        share: DiskShare,
        host: String,
        diskName: String,
        path: String,
        result: MutableList<SmbAudioEntry>,
        depth: Int,
        failedDirs: MutableSet<String>,
        scope: kotlinx.coroutines.CoroutineScope
    ) {
        if (depth > MAX_DEPTH) {
            Timber.e("Max recursion depth (%d) reached for %s", MAX_DEPTH, path)
            return
        }

        val subDirs = mutableListOf<String>()
        val listed = listDirectoryWithRetry(share, path, host, diskName, result, subDirs, failedDirs)
        if (!listed) return

        if (subDirs.isNotEmpty()) {
            val childResults = subDirs.map { dir ->
                scope.async {
                    val partial = mutableListOf<SmbAudioEntry>()
                    try {
                        listRecursive(share, host, diskName, dir, partial, depth + 1, failedDirs, scope)
                    } catch (e: Exception) {
                        Timber.e(e, "Child traversal failed for %s, skipping subtree", dir)
                        failedDirs.add(dir)
                    }
                    partial
                }
            }.awaitAll().flatten()
            synchronized(result) {
                result.addAll(childResults)
            }
        }
    }

    private fun listDirectoryWithRetry(
        share: DiskShare,
        path: String,
        host: String,
        diskName: String,
        result: MutableList<SmbAudioEntry>,
        subDirs: MutableList<String>,
        failedDirs: MutableSet<String>
    ): Boolean {
        for (attempt in 1..LIST_MAX_RETRIES) {
            try {
                val entries = share.list(path)
                for (entry in entries) {
                    val name = entry.fileName
                    if (name == "." || name == "..") continue

                    val fullPath = if (path.isEmpty()) name else "$path/$name"
                    val isDir = (entry.fileAttributes and DIR_ATTR) != 0L

                    if (isDir) {
                        subDirs.add(fullPath)
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
                return true
            } catch (e: Exception) {
                val isRetryable = e is IOException || e is java.net.SocketTimeoutException
                if (attempt < LIST_MAX_RETRIES && isRetryable) {
                    val waitMs = RETRY_BACKOFF_MS[attempt - 1]
                    Timber.w(e, "share.list() retry %d/%d for %s in %dms", attempt, LIST_MAX_RETRIES, path, waitMs)
                    Thread.sleep(waitMs)
                } else {
                    Timber.e(e, "share.list() failed for %s after %d retries, adding to retry queue", path, LIST_MAX_RETRIES)
                    failedDirs.add(path)
                    return false
                }
            }
        }
        return false
    }

    private fun computeMp3RangeLimit(data: ByteArray): Long {
        if (data.size < MP3_HEADER_SIZE) return 262143L
        if (data[0] != 0x49.toByte() || data[1] != 0x44.toByte() || data[2] != 0x33.toByte()) return 262143L

        val synchsafeSize =
            (data[6].toInt() and 0x7F shl 21) or
            (data[7].toInt() and 0x7F shl 14) or
            (data[8].toInt() and 0x7F shl 7) or
            (data[9].toInt() and 0x7F)
        return (MP3_HEADER_SIZE + synchsafeSize).coerceAtMost(1048575L)
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
                val initialLimit = if (extension == "mp3") 262143L else DEFAULT_RANGE_LIMIT
                val bytes = downloadBytes(entry, host, username, password, 0, initialLimit)

                val rangeLimit = if (extension == "mp3") {
                    val tagLimit = computeMp3RangeLimit(bytes)
                    if (tagLimit > bytes.size.toLong()) {
                        val extra = downloadBytes(entry, host, username, password, bytes.size.toLong(), tagLimit - bytes.size.toLong())
                        bytes + extra
                    } else {
                        bytes.copyOf(tagLimit.toInt())
                    }
                } else {
                    bytes
                }

                val source = SeekableDataSources.forBytes(rangeLimit)
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
                    Timber.w(e, "Retry %d/3 for %s in %dms", attempt, entry.relativePath, RETRY_BACKOFF_MS[attempt - 1])
                    Thread.sleep(RETRY_BACKOFF_MS[attempt - 1])
                } else {
                    Timber.e(e, "Metadata extraction failed for %s (attempt %d/3)", entry.relativePath, attempt)
                    return BuildSongResult(null, emptyList())
                }
            }
        }
        return BuildSongResult(null, emptyList())
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
        val share = connectionManager.getShare(session, entry.shareName)
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
        } catch (e: IOException) {
            Timber.w(e, "Download failed, reconnecting for %s", entry.relativePath)
            connectionManager.reconnect(host, username, password)
            throw e
        } finally {
            try { file.close() } catch (_: Exception) {}
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
            val share = connectionManager.getShare(session, shareName)
            val entries = share.list("")
            val fileCount = entries.count {
                val isDir = (it.fileAttributes and DIR_ATTR) != 0L
                !isDir && it.fileName.isAudioFile()
            }
            Result.success(context.getString(R.string.add_source_smb_connected, entries.size, fileCount))
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
