package com.schwanitz.swan.domain.usecase

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.schwanitz.swan.util.Logger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.ID3v1Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.io.File
import java.io.FileOutputStream

class MetadataExtractor private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: MetadataExtractor? = null

        fun getInstance(context: Context): MetadataExtractor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MetadataExtractor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val TAG = "MetadataExtractor"

    fun extractMetadata(uri: Uri): Metadata {
        Logger.d(TAG, "Extracting metadata for URI: $uri")
        var tempFile: File? = null
        try {
            tempFile = createTempFileFromUri(uri, ".mp3")
            Logger.d(TAG, "Created temp file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val artworkCount = tag?.getArtworkList()?.size ?: 0
            val tagVersion = getTagVersion(tag)
            val year = getYear(tag)
            val genre = getFirstAsString(tag, "TCON")
            Logger.d(TAG, "Extracted metadata for ${uri.path}, artwork count: $artworkCount, tag version: $tagVersion, year: $year, genre: $genre")

            return Metadata(
                title = getFirstAsString(tag, "TIT2"),
                artist = getFirstAsString(tag, "TPE1"),
                album = getFirstAsString(tag, "TALB"),
                albumArtist = getFirstAsString(tag, "TPE2"),
                discNumber = getFirstAsString(tag, "TPOS"),
                trackNumber = getFirstAsString(tag, "TRCK"),
                year = year,
                genre = genre,
                artworkCount = artworkCount,
                fileSize = tempFile.length(),
                audioCodec = header?.format ?: "",
                sampleRate = header?.sampleRateAsNumber ?: 0,
                bitrate = header?.bitRateAsNumber?.toLong() ?: 0L,
                tagVersion = tagVersion
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract metadata for ${uri.path}: ${e.message}", e)
            return Metadata(
                title = "",
                artist = "",
                album = "",
                albumArtist = "",
                discNumber = "",
                trackNumber = "",
                year = 0,
                genre = "",
                artworkCount = 0,
                fileSize = 0L,
                audioCodec = "",
                sampleRate = 0,
                bitrate = 0L,
                tagVersion = ""
            )
        } finally {
            tempFile?.delete()
            if (tempFile != null) {
                Logger.d(TAG, "Deleted temp file: ${tempFile.absolutePath}")
            }
        }
    }

    fun getArtworkBytes(uri: Uri, index: Int): ByteArray? {
        Logger.d(TAG, "Extracting artwork at index $index for URI: $uri")
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val embeddedPicture = retriever.embeddedPicture
            retriever.release()
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                if (index == 0) {
                    Logger.d(TAG, "Extracted artwork for ${uri.path}, size: ${embeddedPicture.size} bytes")
                    return embeddedPicture
                }
                Logger.d(TAG, "Multiple artwork not supported via MediaMetadataRetriever, index $index > 0")
            } else {
                Logger.d(TAG, "No embedded picture found for ${uri.path}")
            }
            return null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract artwork at index $index for ${uri.path}: ${e.message}", e)
            return null
        }
    }

    private fun createTempFileFromUri(uri: Uri, defaultExtension: String = ".mp3"): File {
        Logger.d(TAG, "Creating temp file for URI: $uri")
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for URI: $uri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open input stream for URI: $uri, error: ${e.message}", e)
            throw e
        }
        val extension = context.contentResolver.getType(uri)?.let { mimeType ->
            when {
                mimeType.contains("mpeg") -> ".mp3"
                mimeType.contains("flac") -> ".flac"
                mimeType.contains("aac") || mimeType.contains("m4a") -> ".m4a"
                mimeType.contains("wav") -> ".wav"
                else -> defaultExtension
            }
        } ?: uri.lastPathSegment?.substringAfterLast(".", defaultExtension) ?: defaultExtension
        val tempFile = File.createTempFile("audio_${System.nanoTime()}", extension, context.cacheDir)
        FileOutputStream(tempFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
        Logger.d(TAG, "Created temp file: ${tempFile.absolutePath} for URI: $uri, size: ${tempFile.length()} bytes")
        return tempFile
    }

    private fun getTagVersion(tag: Tag?): String {
        return when (tag) {
            is ID3v24Tag -> "ID3v2.4"
            is ID3v23Tag -> "ID3v2.3"
            is ID3v22Tag -> "ID3v2.2"
            is ID3v1Tag -> "ID3v1"
            else -> "Unknown"
        }
    }

    private fun getFirstAsString(tag: Tag?, fieldId: String): String {
        if (tag == null) return ""
        return try {
            val value = tag.getFirst(fieldId)
            if (fieldId == "TCON") {
                // Für Genre: Prüfe auf ID3v1-Format (z. B. "(12)")
                if (value.matches(Regex("\\(\\d+\\)"))) {
                    val mappedGenre = GenreMap.map[value] ?: value
                    Logger.d(TAG, "Mapped ID3v1 genre code $value to $mappedGenre")
                    mappedGenre
                } else if (value.isEmpty()) {
                    Logger.d(TAG, "Genre field $fieldId is empty")
                    ""
                } else {
                    // ID3v2 oder anderer lesbarer Genre-Name
                    value
                }
            } else if (value.isEmpty()) {
                Logger.d(TAG, "Field $fieldId is empty")
                ""
            } else {
                value
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get $fieldId as string: ${e.message}")
            ""
        }
    }

    private fun getYear(tag: Tag?): Int {
        if (tag == null) return 0
        try {
            val fieldsToCheck = listOf("TDRC", "TYER", "TDAT")
            for (fieldId in fieldsToCheck) {
                val fields = tag.getFields(fieldId)
                if (fields.isNotEmpty()) {
                    val rawValue = fields[0].toString()
                    Logger.d(TAG, "Found $fieldId: $rawValue")
                    val yearMatch = Regex("\\d{4}").find(rawValue)
                    if (yearMatch != null) {
                        val yearStr = yearMatch.value
                        if (yearStr.length == 4 && yearStr.all { it.isDigit() }) {
                            return yearStr.toInt()
                        }
                    }
                }
            }
            if (tag is ID3v1Tag && tag.year.isNotEmpty()) {
                val yearStr = tag.year.toString()
                if (yearStr.length == 4 && yearStr.all { it.isDigit() }) {
                    return yearStr.toInt()
                }
            }
            Logger.d(TAG, "No valid year found in tags")
            return 0
        } catch (e: Exception) {
            Logger.e(TAG, "Error extracting year: ${e.message}", e)
            return 0
        }
    }
}

data class Metadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val discNumber: String,
    val trackNumber: String,
    val year: Int = 0,
    val genre: String,
    val artworkCount: Int,
    val fileSize: Long,
    val audioCodec: String,
    val sampleRate: Int,
    val bitrate: Long,
    val tagVersion: String
)