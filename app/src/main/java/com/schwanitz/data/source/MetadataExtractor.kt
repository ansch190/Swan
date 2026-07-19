package com.schwanitz.data.source

import android.content.Context
import android.net.Uri
import com.schwanitz.domain.model.Song
import com.schwanitz.api.MetadataManager
import com.schwanitz.interfaces.Metadata
import com.schwanitz.io.SeekableDataSource
import com.schwanitz.metadata.PictureData
import com.schwanitz.tagging.ScanConfiguration
import timber.log.Timber
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val tagixExecutor = Executors.newCachedThreadPool { r ->
    Thread(r, "tagix-scanner").also { it.isDaemon = true }
}

data class BuildSongResult(
    val song: Song?,
    val artworkUris: List<String>,
    val artworks: List<ArtworkResult> = emptyList()
)

data class TextFields(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val discRaw: String = "",
    val trackRaw: String = "",
    val year: Int = 0,
    val genre: String = "",
    val tagVersion: String = ""
)

object MetadataExtractor {

    private val metadataManager = MetadataManager()

    fun buildSong(
        source: SeekableDataSource,
        songId: String,
        sourceId: String,
        context: Context,
        fileSize: Long = 0L,
        albumArtworkCache: MutableMap<String, List<String>> = mutableMapOf(),
        fileExtension: String = ""
    ): BuildSongResult {
        return try {
            val metadataList: List<Metadata>
            try {
                Timber.d("Tagix scan start: %s", songId)
                val t0 = System.currentTimeMillis()
                val future = tagixExecutor.submit(Callable {
                    metadataManager.readFromSource(source, ScanConfiguration.comfortScan())
                })
                metadataList = try {
                    future.get(15, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    future.cancel(true)
                    Timber.w("Tagix scan timed out for %s", songId)
                    emptyList()
                }
                val dt = System.currentTimeMillis() - t0
                if (metadataList.isNotEmpty()) {
                    Timber.d("Tagix scan done: %d metadata(s) in %dms for %s", metadataList.size, dt, songId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Tagix scan failed for %s", songId)
                return BuildSongResult(null, emptyList())
            }

            val bestMetadata = metadataList.maxByOrNull { tagPriority(it.tagFormat) }
                ?: metadataList.firstOrNull()

            val textFields = if (bestMetadata != null) {
                Timber.d("Tag format=%s, fields=%d, pictures=%d", bestMetadata.tagFormat, bestMetadata.fields.size, bestMetadata.pictures.size)
                extractTextFields(bestMetadata)
            } else {
                Timber.w("No tag metadata found for %s, using defaults", songId)
                TextFields()
            }

            val fileName = try {
                val decoded = Uri.decode(songId)
                val lastPart = decoded.substringAfterLast('/')
                val filenameWithExt = if (lastPart.contains(':')) lastPart.substringAfterLast(':') else lastPart
                if (filenameWithExt.contains('.')) filenameWithExt.substringBeforeLast('.') else filenameWithExt
            } catch (e: Exception) {
                songId
            }

            val finalTitle = textFields.title.ifBlank { fileName }
            Timber.d("Extracted for %s: title=%s artist=%s album=%s albumArtist=%s disc=%s track=%s year=%d genre=%s tag=%s", songId, textFields.title, textFields.artist, textFields.album, textFields.albumArtist, textFields.discRaw, textFields.trackRaw, textFields.year, textFields.genre, textFields.tagVersion)

            val mimeType = extensionToMimeType(fileExtension)

            val albumKey = "${textFields.albumArtist.trim()}|${textFields.album.trim()}|${textFields.year}"

            val allUris: List<String>
            val allArtworks: List<ArtworkResult>

            if (albumKey.isNotEmpty()) {
                val placeholder = albumArtworkCache.putIfAbsent(albumKey, emptyList())
                if (placeholder != null) {
                    allUris = placeholder
                    allArtworks = emptyList()
                    Timber.d("Artwork cache HIT for album key: %s (%d URIs)", albumKey, allUris.size)
                } else {
                    val artworkResults = if (bestMetadata != null) saveArtwork(bestMetadata, context) else emptyList()
                    val artworkUris = artworkResults.map { it.largeUri }

                    if (artworkUris.isNotEmpty()) {
                        albumArtworkCache[albumKey] = artworkUris
                        Timber.d("Artwork cache STORE for album key: %s (%d URIs)", albumKey, artworkUris.size)
                    }
                    allUris = artworkUris
                    allArtworks = artworkResults
                }
            } else {
                allUris = emptyList()
                allArtworks = emptyList()
            }

            val song = Song(
                id = songId,
                title = finalTitle,
                artistName = textFields.artist.trim(),
                albumName = textFields.album.trim(),
                durationMs = 0L,
                sourceId = sourceId,
                albumArtistName = textFields.albumArtist.trim(),
                discNumber = textFields.discRaw.trim().substringBefore('/').filter { it.isDigit() }.toIntOrNull() ?: 0,
                trackNumber = textFields.trackRaw.trim().substringBefore('/').filter { it.isDigit() }.toIntOrNull() ?: 0,
                year = textFields.year,
                genre = textFields.genre,
                mimeType = mimeType,
                sampleRate = 0,
                bitrate = 0,
                tagVersion = textFields.tagVersion,
                fileSize = fileSize
            )

            BuildSongResult(song, allUris, allArtworks)
        } catch (e: Exception) {
            Timber.e(e, "buildSong failed for %s", songId)
            BuildSongResult(null, emptyList())
        }
    }

    private fun extensionToMimeType(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "opus" -> "audio/opus"
        "wav" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        "aac" -> "audio/aac"
        else -> "audio/mpeg"
    }

    private fun extractTextFields(metadata: Metadata): TextFields {
        var title = ""
        var artist = ""
        var album = ""
        var albumArtist = ""
        var discRaw = ""
        var trackRaw = ""
        var year = 0
        var genre = ""

        for (field in metadata.fields) {
            if (field.value is PictureData) continue
            val value = field.value?.toString()?.trim() ?: continue

            val key = field.key.uppercase()
            Timber.d("  field: %s -> %s", field.key, value)
            when (key) {
                "TITLE", "TIT2", "\u00a9NAM" -> if (title.isEmpty()) title = value
                "ARTIST", "TPE1", "\u00a9ART" -> if (artist.isEmpty()) artist = value
                "ALBUM", "TALB", "\u00a9ALB" -> if (album.isEmpty()) album = value
                "ALBUMARTIST", "TPE2", "ALBUM ARTIST" -> if (albumArtist.isEmpty()) albumArtist = value
                "DISCNUMBER", "TPOS", "DISC" -> discRaw = value
                "TRACKNUMBER", "TRCK", "TRKN", "TRACK" -> trackRaw = value
                "DATE", "TYER", "YEAR", "TDRC", "\u00a9DAY" -> {
                    year = Regex("\\b(\\d{4})\\b").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
                "GENRE", "TCON", "\u00a9GEN" -> genre = value
            }
        }
        Timber.d("  extracted: title=%s artist=%s album=%s albumArtist=%s disc=%s track=%s year=%d genre=%s", title, artist, album, albumArtist, discRaw, trackRaw, year, genre)

        return TextFields(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            discRaw = discRaw,
            trackRaw = trackRaw,
            year = year,
            genre = genre,
            tagVersion = formatTagixVersion(metadata.tagFormat)
        )
    }

    private fun saveArtwork(metadata: Metadata, context: Context): List<ArtworkResult> {
        return metadata.pictures.mapIndexed { index, picture ->
            Timber.d("  picture[%d]: %d bytes, mime=%s", index, picture.data.size, picture.mimeType)
            ArtworkCache.saveScaled(picture.data, context, index)
        }
    }

    private fun tagPriority(tagFormat: String): Int = when {
        tagFormat.contains("2.4") -> 5
        tagFormat.contains("2.3") -> 4
        tagFormat.startsWith("MP4") -> 4
        tagFormat.startsWith("AIFF") -> 4
        tagFormat.startsWith("FLAC") -> 3
        tagFormat.startsWith("Vorbis") -> 3
        tagFormat.startsWith("Matroska") -> 3
        tagFormat.startsWith("WavPack") -> 3
        tagFormat.startsWith("APE") -> 3
        tagFormat.contains("1.1") -> 1
        tagFormat.contains("ID3v1") -> 0
        else -> -1
    }

    private fun formatTagixVersion(tagFormat: String): String {
        return when {
            tagFormat.equals("VorbisComment", ignoreCase = true) -> "Vorbis"
            tagFormat.equals("MP4", ignoreCase = true) -> "MP4/M4A"
            else -> tagFormat
        }
    }
}
