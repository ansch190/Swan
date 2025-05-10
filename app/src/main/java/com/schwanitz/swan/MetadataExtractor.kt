package com.schwanitz.swan

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.id3.ID3v1Tag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Tag
import java.io.File
import java.io.FileOutputStream

class MetadataExtractor(private val context: Context) {

    private val TAG = "MetadataExtractor"

    fun extractMetadata(uri: Uri): Metadata {
        Log.d(TAG, "Extracting metadata for URI: $uri")
        try {
            // Konvertiere URI zu einer temporären Datei mit korrekter Erweiterung
            val tempFile = createTempFileFromUri(uri, ".mp3")
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val artworkCount = tag?.getArtworkList()?.size ?: 0
            val tagVersion = getTagVersion(tag)
            val year = getYear(tag)
            Log.d(TAG, "Extracted metadata for ${uri.path}, artwork count: $artworkCount, tag version: $tagVersion, year: $year")

            return Metadata(
                title = getFirstAsString(tag, "TIT2"),
                artist = getFirstAsString(tag, "TPE1"),
                album = getFirstAsString(tag, "TALB"),
                albumArtist = getFirstAsString(tag, "TPE2"),
                discNumber = getFirstAsString(tag, "TPOS"),
                trackNumber = getFirstAsString(tag, "TRCK"),
                year = year,
                genre = getFirstAsString(tag, "TCON"),
                artworkCount = artworkCount,
                fileSize = tempFile.length().toInt(),
                audioCodec = header?.format ?: "",
                sampleRate = header?.sampleRateAsNumber ?: 0,
                bitrate = header?.bitRateAsNumber?.toLong() ?: 0L,
                tagVersion = tagVersion
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata for ${uri.path}: ${e.message}", e)
            return Metadata(
                title = "",
                artist = "",
                album = "",
                albumArtist = "",
                discNumber = "",
                trackNumber = "",
                year = "",
                genre = "",
                artworkCount = 0,
                fileSize = 0,
                audioCodec = "",
                sampleRate = 0,
                bitrate = 0L,
                tagVersion = ""
            )
        } finally {
            // Lösche temporäre Datei
            val tempFile = File(context.cacheDir, "audio.mp3")
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(TAG, "Deleted temp file: ${tempFile.absolutePath}")
            }
        }
    }

    fun getArtworkBytes(uri: Uri, index: Int): ByteArray? {
        Log.d(TAG, "Extracting artwork at index $index for URI: $uri")
        try {
            val tempFile = createTempFileFromUri(uri, ".mp3")
            Log.d(TAG, "Created temp file for artwork: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            val artwork = tag?.getArtworkList()?.getOrNull(index)
            val bytes = artwork?.binaryData
            Log.d(TAG, "Extracted artwork at index $index for ${uri.path}, size: ${bytes?.size ?: 0} bytes")
            return bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract artwork at index $index for ${uri.path}: ${e.message}", e)
            return null
        } finally {
            // Lösche temporäre Datei
            val tempFile = File(context.cacheDir, "audio.mp3")
            if (tempFile.exists()) {
                tempFile.delete()
                Log.d(TAG, "Deleted temp file: ${tempFile.absolutePath}")
            }
        }
    }

    private fun createTempFileFromUri(uri: Uri, defaultExtension: String = ".mp3"): File {
        Log.d(TAG, "Creating temp file for URI: $uri")
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for URI: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for URI: $uri, error: ${e.message}", e)
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
        Log.d(TAG, "Created temp file: ${tempFile.absolutePath} for URI: $uri, size: ${tempFile.length()} bytes")
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
            if (value.isEmpty()) {
                Log.d(TAG, "Field $fieldId is empty")
                ""
            } else {
                value
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get $fieldId as string: ${e.message}")
            ""
        }
    }

    private fun getYear(tag: Tag?): String {
        if (tag == null) return ""
        try {
            // Liste der zu überprüfenden Felder
            val fieldsToCheck = listOf("TDRC", "TYER", "TDAT")
            for (fieldId in fieldsToCheck) {
                val fields = tag.getFields(fieldId)
                if (fields.isNotEmpty()) {
                    // Ersten Wert als String extrahieren
                    val rawValue = fields[0].toString()
                    Log.d(TAG, "Found $fieldId: $rawValue")
                    // 4-stelliges Jahr mit Regex finden
                    val yearMatch = Regex("\\d{4}").find(rawValue)
                    if (yearMatch != null) {
                        val year: String = yearMatch.value
                        if (year.length == 4 && year.all { it.isDigit() }) {
                            return year
                        }
                    }
                }
            }
            // Fallback für ID3v1-Tags
            if (tag is ID3v1Tag && tag.year.isNotEmpty()) {
                val year: String = tag.year.toString()
                if (year.length == 4 && year.all { it.isDigit() }) {
                    return year
                }
            }
            Log.d(TAG, "No valid year found in tags")
            return ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting year: ${e.message}", e)
            return ""
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
    val year: String,
    val genre: String,
    val artworkCount: Int,
    val fileSize: Int,
    val audioCodec: String,
    val sampleRate: Int,
    val bitrate: Long,
    val tagVersion: String
)