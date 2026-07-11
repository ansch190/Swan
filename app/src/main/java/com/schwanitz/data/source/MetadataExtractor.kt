package com.schwanitz.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.schwanitz.domain.model.Song
import com.schwanitz.api.MetadataManager
import com.schwanitz.interfaces.Metadata
import com.schwanitz.io.SeekableDataSource
import com.schwanitz.metadata.PictureData
import com.schwanitz.tagging.ScanConfiguration
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
        retriever: MediaMetadataRetriever,
        songId: String,
        sourceId: String,
        context: Context,
        fileSize: Long = 0L,
        albumArtworkCache: MutableMap<String, List<String>> = mutableMapOf()
    ): BuildSongResult {
        return try {
            val metadataList: List<Metadata>
            try {
                Log.e("MetadataExtractor", "Tagix scan start: $songId")
                val t0 = System.currentTimeMillis()
                val future = tagixExecutor.submit(Callable {
                    metadataManager.readFromSource(source, ScanConfiguration.comfortScan())
                })
                metadataList = try {
                    future.get(15, TimeUnit.SECONDS)
                } catch (e: TimeoutException) {
                    future.cancel(true)
                    Log.e("MetadataExtractor", "Tagix scan timed out for $songId")
                    emptyList()
                }
                val dt = System.currentTimeMillis() - t0
                if (metadataList.isNotEmpty()) {
                    Log.e("MetadataExtractor", "Tagix scan done: ${metadataList.size} metadata(s) in ${dt}ms for $songId")
                }
            } catch (e: Exception) {
                Log.e("MetadataExtractor", "Tagix scan failed for $songId", e)
                return BuildSongResult(null, emptyList())
            }

            val bestMetadata = metadataList.maxByOrNull { tagPriority(it.tagFormat) }
                ?: metadataList.firstOrNull()

            val textFields = if (bestMetadata != null) {
                Log.e("MetadataExtractor", "Tag format=${bestMetadata.tagFormat}, fields=${bestMetadata.fields.size}, pictures=${bestMetadata.pictures.size}")
                extractTextFields(bestMetadata)
            } else {
                Log.w("MetadataExtractor", "No tag metadata found for $songId, using defaults")
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
            Log.e("MetadataExtractor", "Extracted for $songId: title=${textFields.title} artist=${textFields.artist} album=${textFields.album} albumArtist=${textFields.albumArtist} disc=${textFields.discRaw} track=${textFields.trackRaw} year=${textFields.year} genre=${textFields.genre} tag=${textFields.tagVersion}")

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            val albumKey = "${textFields.albumArtist.trim()}|${textFields.album.trim()}|${textFields.year}"

            val allUris: List<String>
            val allArtworks: List<ArtworkResult>

            if (albumKey.isNotEmpty() && albumKey in albumArtworkCache) {
                allUris = albumArtworkCache[albumKey]!!
                allArtworks = emptyList()
                Log.e("MetadataExtractor", "Artwork cache HIT for album key: $albumKey (${allUris.size} URIs)")
            } else {
                val mmrUris = mutableListOf<String>()
                val artworkResults = mutableListOf<ArtworkResult>()

                val mmrArtworkBytes = retriever.embeddedPicture
                if (mmrArtworkBytes != null) {
                    val result = ArtworkCache.saveScaled(mmrArtworkBytes, context, 0)
                    mmrUris.add(result.largeUri)
                    artworkResults.add(result)
                }

                val tagArtResults = if (bestMetadata != null) saveArtwork(bestMetadata, context) else emptyList()
                val tagUris = tagArtResults.map { it.largeUri }
                allUris = (mmrUris + tagUris.filter { it !in mmrUris })

                allArtworks = artworkResults + tagArtResults.filter { it.largeUri !in mmrUris }

                if (albumKey.isNotEmpty() && allUris.isNotEmpty()) {
                    albumArtworkCache[albumKey] = allUris
                    Log.e("MetadataExtractor", "Artwork cache STORE for album key: $albumKey (${allUris.size} URIs)")
                }
            }

            val song = Song(
                id = songId,
                title = finalTitle,
                artistName = textFields.artist.trim(),
                albumName = textFields.album.trim(),
                durationMs = durationMs,
                sourceId = sourceId,
                albumArtistName = textFields.albumArtist.trim(),
                discNumber = textFields.discRaw.trim().substringBefore('/').filter { it.isDigit() }.toIntOrNull() ?: 0,
                trackNumber = textFields.trackRaw.trim().substringBefore('/').filter { it.isDigit() }.toIntOrNull() ?: 0,
                year = textFields.year,
                genre = textFields.genre,
                mimeType = mimeType,
                sampleRate = sampleRate,
                bitrate = bitrate,
                tagVersion = textFields.tagVersion,
                fileSize = fileSize
            )

            BuildSongResult(song, allUris, allArtworks)
        } catch (e: Exception) {
            Log.e("MetadataExtractor", "buildSong failed for $songId", e)
            BuildSongResult(null, emptyList())
        }
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
            Log.e("MetadataExtractor", "  field: ${field.key} -> $value")
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
        Log.e("MetadataExtractor", "  extracted: title=$title artist=$artist album=$album albumArtist=$albumArtist disc=$discRaw track=$trackRaw year=$year genre=$genre")

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
            Log.e("MetadataExtractor", "  picture[$index]: ${picture.data.size} bytes, mime=${picture.mimeType}")
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
