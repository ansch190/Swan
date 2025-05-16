package com.schwanitz.swan.domain.usecase

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

    // Vollständige ID3v1-Genre-Liste, einschließlich Winamp-Erweiterungen
    private val genreMap = mapOf(
        "(0)" to "Blues",
        "(1)" to "Classic Rock",
        "(2)" to "Country",
        "(3)" to "Dance",
        "(4)" to "Disco",
        "(5)" to "Funk",
        "(6)" to "Grunge",
        "(7)" to "Hip-Hop",
        "(8)" to "Jazz",
        "(9)" to "Metal",
        "(10)" to "New Age",
        "(11)" to "Oldies",
        "(12)" to "Other",
        "(13)" to "Pop",
        "(14)" to "R&B",
        "(15)" to "Rap",
        "(16)" to "Reggae",
        "(17)" to "Rock",
        "(18)" to "Techno",
        "(19)" to "Industrial",
        "(20)" to "Alternative",
        "(21)" to "Ska",
        "(22)" to "Death Metal",
        "(23)" to "Pranks",
        "(24)" to "Soundtrack",
        "(25)" to "Euro-Techno",
        "(26)" to "Ambient",
        "(27)" to "Trip-Hop",
        "(28)" to "Vocal",
        "(29)" to "Jazz+Funk",
        "(30)" to "Fusion",
        "(31)" to "Trance",
        "(32)" to "Classical",
        "(33)" to "Instrumental",
        "(34)" to "Acid",
        "(35)" to "House",
        "(36)" to "Game",
        "(37)" to "Sound Clip",
        "(38)" to "Gospel",
        "(39)" to "Noise",
        "(40)" to "AlternRock",
        "(41)" to "Bass",
        "(42)" to "Soul",
        "(43)" to "Punk",
        "(44)" to "Space",
        "(45)" to "Meditative",
        "(46)" to "Instrumental Pop",
        "(47)" to "Instrumental Rock",
        "(48)" to "Ethnic",
        "(49)" to "Gothic",
        "(50)" to "Darkwave",
        "(51)" to "Techno-Industrial",
        "(52)" to "Electronic",
        "(53)" to "Pop-Folk",
        "(54)" to "Eurodance",
        "(55)" to "Dream",
        "(56)" to "Southern Rock",
        "(57)" to "Comedy",
        "(58)" to "Cult",
        "(59)" to "Gangsta",
        "(60)" to "Top 40",
        "(61)" to "Christian Rap",
        "(62)" to "Pop/Funk",
        "(63)" to "Jungle",
        "(64)" to "Native American",
        "(65)" to "Cabaret",
        "(66)" to "New Wave",
        "(67)" to "Psychedelic",
        "(68)" to "Rave",
        "(69)" to "Showtunes",
        "(70)" to "Trailer",
        "(71)" to "Lo-Fi",
        "(72)" to "Tribal",
        "(73)" to "Acid Punk",
        "(74)" to "Acid Jazz",
        "(75)" to "Polka",
        "(76)" to "Retro",
        "(77)" to "Musical",
        "(78)" to "Rock & Roll",
        "(79)" to "Hard Rock",
        //Folgend WinAmp Extension
        "(80)" to "Folk",
        "(81)" to "Folk-Rock",
        "(82)" to "National Folk",
        "(83)" to "Swing",
        "(84)" to "Fast Fusion",
        "(85)" to "Bebop",
        "(86)" to "Latin",
        "(87)" to "Revival",
        "(88)" to "Celtic",
        "(89)" to "Bluegrass",
        "(90)" to "Avantgarde",
        "(91)" to "Gothic Rock",
        "(92)" to "Progressive Rock",
        "(93)" to "Psychedelic Rock",
        "(94)" to "Symphonic Rock",
        "(95)" to "Slow Rock",
        "(96)" to "Big Band",
        "(97)" to "Chorus",
        "(98)" to "Easy Listening",
        "(99)" to "Acoustic",
        "(100)" to "Humour",
        "(101)" to "Speech",
        "(102)" to "Chanson",
        "(103)" to "Opera",
        "(104)" to "Chamber Music",
        "(105)" to "Sonata",
        "(106)" to "Symphony",
        "(107)" to "Booty Bass",
        "(108)" to "Primus",
        "(109)" to "Porn Groove",
        "(110)" to "Satire",
        "(111)" to "Slow Jam",
        "(112)" to "Club",
        "(113)" to "Tango",
        "(114)" to "Samba",
        "(115)" to "Folklore",
        "(116)" to "Ballad",
        "(117)" to "Power Ballad",
        "(118)" to "Rhythmic Soul",
        "(119)" to "Freestyle",
        "(120)" to "Duet",
        "(121)" to "Punk Rock",
        "(122)" to "Drum Solo",
        "(123)" to "A Cappella",
        "(124)" to "Euro-House",
        "(125)" to "Dance Hall",
        "(126)" to "Goa",
        "(127)" to "Drum & Bass",
        "(128)" to "Club-House",
        "(129)" to "Hardcore",
        "(130)" to "Terror",
        "(131)" to "Indie",
        "(132)" to "BritPop",
        "(133)" to "Negerpunk",
        "(134)" to "Polsk Punk",
        "(135)" to "Beat",
        "(136)" to "Christian Gangsta Rap",
        "(137)" to "Heavy Metal",
        "(138)" to "Black Metal",
        "(139)" to "Crossover",
        "(140)" to "Contemporary Christian",
        "(141)" to "Christian Rock",
        "(142)" to "Merengue",
        "(143)" to "Salsa",
        "(144)" to "Thrash Metal",
        "(145)" to "Anime",
        "(146)" to "JPop",
        "(147)" to "Synthpop",
        "(148)" to "Abstract",
        "(149)" to "Art Rock",
        "(150)" to "Baroque",
        "(151)" to "Bhangra",
        "(152)" to "Big Beat",
        "(153)" to "Breakbeat",
        "(154)" to "Chillout",
        "(155)" to "Downtempo",
        "(156)" to "Dub",
        "(157)" to "EBM",
        "(158)" to "Eclectic",
        "(159)" to "Electro",
        "(160)" to "Electroclash",
        "(161)" to "Emo",
        "(162)" to "Experimental",
        "(163)" to "Garage",
        "(164)" to "Global",
        "(165)" to "IDM",
        "(166)" to "Illbient",
        "(167)" to "Industro-Goth",
        "(168)" to "Jam Band",
        "(169)" to "Krautrock",
        "(170)" to "Leftfield",
        "(171)" to "Lounge",
        "(172)" to "Math Rock",
        "(173)" to "New Romantic",
        "(174)" to "Nu-Breakz",
        "(175)" to "Post-Punk",
        "(176)" to "Post-Rock",
        "(177)" to "Psytrance",
        "(178)" to "Shoegaze",
        "(179)" to "Space Rock",
        "(180)" to "Trop Rock",
        "(181)" to "World Music",
        "(182)" to "Neoclassical",
        "(183)" to "Audiobook",
        "(184)" to "Audio Theatre",
        "(185)" to "Neue Deutsche Welle",
        "(186)" to "Podcast",
        "(187)" to "Indie Rock",
        "(188)" to "G-Funk",
        "(189)" to "Dubstep",
        "(190)" to "Garage Rock",
        "(191)" to "Psybient"
    )

    fun extractMetadata(uri: Uri): Metadata {
        Log.d(TAG, "Extracting metadata for URI: $uri")
        try {
            val tempFile = createTempFileFromUri(uri, ".mp3")
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val artworkCount = tag?.getArtworkList()?.size ?: 0
            val tagVersion = getTagVersion(tag)
            val year = getYear(tag)
            val genre = getFirstAsString(tag, "TCON")
            Log.d(TAG, "Extracted metadata for ${uri.path}, artwork count: $artworkCount, tag version: $tagVersion, year: $year, genre: $genre")

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
            if (fieldId == "TCON") {
                // Für Genre: Prüfe auf ID3v1-Format (z. B. "(12)")
                if (value.matches(Regex("\\(\\d+\\)"))) {
                    val mappedGenre = genreMap[value] ?: value
                    Log.d(TAG, "Mapped ID3v1 genre code $value to $mappedGenre")
                    mappedGenre
                } else if (value.isEmpty()) {
                    Log.d(TAG, "Genre field $fieldId is empty")
                    ""
                } else {
                    // ID3v2 oder anderer lesbarer Genre-Name
                    value
                }
            } else if (value.isEmpty()) {
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
            val fieldsToCheck = listOf("TDRC", "TYER", "TDAT")
            for (fieldId in fieldsToCheck) {
                val fields = tag.getFields(fieldId)
                if (fields.isNotEmpty()) {
                    val rawValue = fields[0].toString()
                    Log.d(TAG, "Found $fieldId: $rawValue")
                    val yearMatch = Regex("\\d{4}").find(rawValue)
                    if (yearMatch != null) {
                        val year: String = yearMatch.value
                        if (year.length == 4 && year.all { it.isDigit() }) {
                            return year
                        }
                    }
                }
            }
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