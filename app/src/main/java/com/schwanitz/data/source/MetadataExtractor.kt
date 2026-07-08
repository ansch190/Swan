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
    val artworkUris: List<String>
)

data class TagFields(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val discRaw: String = "",
    val trackRaw: String = "",
    val year: Int = 0,
    val genre: String = "",
    val tagVersion: String = "",
    val artworkUris: List<String> = emptyList()
)

object MetadataExtractor {

    private val metadataManager = MetadataManager()

    fun buildSong(
        source: SeekableDataSource,
        retriever: MediaMetadataRetriever,
        songId: String,
        sourceId: String,
        context: Context,
        fileSize: Long = 0L
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

            val tagFields = if (metadataList.isEmpty()) {
                Log.w("MetadataExtractor", "No tag metadata found for $songId, using defaults")
                TagFields()
            } else {
                val bestMetadata = metadataList.maxByOrNull { tagPriority(it.tagFormat) }
                    ?: metadataList.first()
                Log.e("MetadataExtractor", "Tag format=${bestMetadata.tagFormat}, fields=${bestMetadata.fields.size}, pictures=${bestMetadata.pictures.size}")
                extractTagFields(bestMetadata, context)
            }

            val fileName = try {
                val decoded = Uri.decode(songId)
                val lastPart = decoded.substringAfterLast('/')
                val filenameWithExt = if (lastPart.contains(':')) lastPart.substringAfterLast(':') else lastPart
                if (filenameWithExt.contains('.')) filenameWithExt.substringBeforeLast('.') else filenameWithExt
            } catch (e: Exception) {
                songId
            }

            val finalTitle = tagFields.title.ifBlank { fileName }
            Log.e("MetadataExtractor", "Extracted for $songId: title=${tagFields.title} artist=${tagFields.artist} album=${tagFields.album} albumArtist=${tagFields.albumArtist} disc=${tagFields.discRaw} track=${tagFields.trackRaw} year=${tagFields.year} genre=${tagFields.genre} tag=${tagFields.tagVersion} artworks=${tagFields.artworkUris.size}")

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            val mmrArtworkBytes = retriever.embeddedPicture

            val allUris = mutableListOf<String>()
            if (mmrArtworkBytes != null) {
                val uri = ArtworkCache.save(mmrArtworkBytes, context, 0)
                allUris.add(uri)
            }
            allUris.addAll(tagFields.artworkUris.filter { it !in allUris })

            val song = Song(
                id = songId,
                title = finalTitle,
                artist = tagFields.artist,
                album = tagFields.album,
                durationMs = durationMs,
                albumArtUri = allUris.firstOrNull(),
                sourceId = sourceId,
                albumArtist = tagFields.albumArtist,
                discNumber = tagFields.discRaw.substringBefore("/").trim().toIntOrNull() ?: 0,
                trackNumber = tagFields.trackRaw.substringBefore("/").trim().toIntOrNull() ?: 0,
                trackRaw = tagFields.trackRaw,
                discRaw = tagFields.discRaw,
                year = tagFields.year,
                genre = tagFields.genre,
                mimeType = mimeType,
                sampleRate = sampleRate,
                bitrate = bitrate,
                tagVersion = tagFields.tagVersion,
                fileSize = fileSize
            )

            BuildSongResult(song, allUris)
        } catch (e: Exception) {
            Log.e("MetadataExtractor", "buildSong failed for $songId", e)
            BuildSongResult(null, emptyList())
        }
    }

    private fun extractTagFields(metadata: Metadata, context: Context): TagFields {
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
                "ALBUMARTIST", "TPE2", "ALBUM ARTIST" -> albumArtist = value
                "DISCNUMBER", "TPOS", "DISC" -> discRaw = value
                "TRACKNUMBER", "TRCK", "TRKN", "TRACK" -> trackRaw = value
                "DATE", "TYER", "YEAR", "TDRC", "\u00a9DAY" -> {
                    year = Regex("\\b(\\d{4})\\b").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
                "GENRE", "TCON", "\u00a9GEN" -> genre = value
            }
        }
        Log.e("MetadataExtractor", "  extracted: title=$title artist=$artist album=$album albumArtist=$albumArtist disc=$discRaw track=$trackRaw year=$year genre=$genre")

        val artworkUris = metadata.pictures.mapIndexed { index, picture ->
            Log.e("MetadataExtractor", "  picture[$index]: ${picture.data.size} bytes, mime=${picture.mimeType}")
            ArtworkCache.save(picture.data, context, index)
        }

        return TagFields(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            discRaw = discRaw,
            trackRaw = trackRaw,
            year = year,
            genre = genre,
            tagVersion = formatTagixVersion(metadata.tagFormat),
            artworkUris = artworkUris
        )
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
