package com.schwanitz.data.source.smb

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class SmbDataSource(
    private val connectionManager: SmbConnectionManager
) : BaseDataSource(false) {

    private var inputStream: InputStream? = null
    private var totalBytes = 0L
    private var bytesRead = 0L
    private var currentUri: Uri? = null

    private var session: com.hierynomus.smbj.session.Session? = null
    private var smbFile: com.hierynomus.smbj.share.File? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        close()
        val uri = dataSpec.uri
        currentUri = uri

        val parsed = parseSmbUri(uri.toString())
            ?: throw IOException("Invalid SMB URI: $uri")

        val config = findConfig(parsed.host)
        val username = config?.username ?: ""
        val password = config?.password ?: ""

        Timber.d("SMB open: host=%s share=%s path=%s", parsed.host, parsed.share, parsed.filePath)

        val sess = connectionManager.connect(parsed.host, username, password)
        session = sess

        val file = connectionManager.openFile(sess, parsed.share, parsed.filePath)
        smbFile = file

        totalBytes = file.getFileInformation(FileStandardInformation::class.java).endOfFile
        inputStream = file.getInputStream()
        bytesRead = 0L

        val remaining = if (dataSpec.length != Long.MAX_VALUE) {
            minOf(dataSpec.length, totalBytes - dataSpec.position)
        } else {
            totalBytes - dataSpec.position
        }

        if (dataSpec.position > 0) {
            skipBytes(dataSpec.position)
        }

        transferStarted(dataSpec)
        Timber.d("SMB opened: %d bytes, remaining=%d", totalBytes, remaining)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val stream = inputStream ?: throw IOException("SMB not opened")
        val read = stream.read(buffer, offset, length)
        if (read > 0) {
            bytesRead += read
            bytesTransferred(read)
        }
        return read
    }

    override fun close() {
        try { inputStream?.close() } catch (_: Exception) {}
        try { smbFile?.close() } catch (_: Exception) {}
        inputStream = null
        smbFile = null
        session = null
        totalBytes = 0L
        bytesRead = 0L
    }

    override fun getUri(): Uri? = currentUri

    private fun skipBytes(n: Long) {
        val stream = inputStream ?: return
        var remaining = n
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = stream.read(buf, 0, toRead)
            if (read <= 0) break
            remaining -= read
            bytesRead += read
        }
    }

    private fun findConfig(host: String): SourceConfig? {
        for ((_, config) in sourceConfigs) {
            if (config.type == SourceType.SMB && config.isEnabled) {
                val configHost = config.url?.lowercase()?.trimEnd('/')
                    ?: continue
                if (host.lowercase() == configHost || host.lowercase().contains(configHost)) {
                    return config
                }
            }
        }
        return null
    }

    companion object {
        private val sourceConfigs = ConcurrentHashMap<String, SourceConfig>()

        fun updateSources(sources: List<SourceConfig>) {
            sourceConfigs.clear()
            sources
                .filter { it.type == SourceType.SMB && it.isEnabled }
                .forEach { config ->
                    sourceConfigs[config.id] = config
                }
        }

        fun parseSmbUri(uri: String): SmbUri? {
            val clean = uri.removePrefix("smb://")
            val slashIndex = clean.indexOf('/')
            if (slashIndex < 0) return null
            val hostPort = clean.substring(0, slashIndex)
            val host = hostPort.substringBefore(':')
            val rest = clean.substring(slashIndex + 1)
            val nextSlash = rest.indexOf('/')
            if (nextSlash < 0) return SmbUri(host, rest, "")
            val share = rest.substring(0, nextSlash)
            val filePath = URLDecoder.decode(rest.substring(nextSlash + 1), "UTF-8")
            return SmbUri(host, share, filePath)
        }
    }

    data class SmbUri(val host: String, val share: String, val filePath: String)
}
