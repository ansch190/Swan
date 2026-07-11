package com.schwanitz.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.source.LoadSongsResult
import com.schwanitz.domain.source.MusicSource
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFolderMusicSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicSource {

    override val type: SourceType = SourceType.LOCAL

    override suspend fun loadSongs(
        config: SourceConfig,
        onProgress: (Int, Int) -> Unit
    ): LoadSongsResult = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(config.folderUri ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap()))
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())

        val audioUris = mutableListOf<Uri>()
        collectAudioFiles(documentFile, audioUris)

        val total = audioUris.size
        val songs = mutableListOf<Song>()
        val albumMap = mutableMapOf<String, com.schwanitz.domain.model.Album>()
        val albumArtworkMap = mutableMapOf<String, MutableList<AlbumArtwork>>()
        val albumArtworkCache = mutableMapOf<String, List<String>>()

        Log.e("LocalFolderMusicSource", "Starting scan: $total files")
        audioUris.forEachIndexed { index, uri ->
            val t0 = System.currentTimeMillis()
            onProgress(index + 1, total)

            try {
                val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: run {
                        Log.w("LocalFolderMusicSource", "[${index + 1}/$total] Cannot open $uri")
                        return@forEachIndexed
                    }
                try {
                    val source = ContentUriDataSource(afd, uri.toString())
                    val fileSize = source.length()
                    val retriever = MediaMetadataRetriever()
                    try {
                        val fd = afd.parcelFileDescriptor.fileDescriptor
                        if (afd.declaredLength >= 0L) {
                            retriever.setDataSource(fd, afd.startOffset, afd.declaredLength)
                        } else {
                            retriever.setDataSource(fd)
                        }
                        val result = MetadataExtractor.buildSong(
                            source, retriever, uri.toString(), config.id, context, fileSize,
                            albumArtworkCache = albumArtworkCache
                        )
                        val totalMs = System.currentTimeMillis() - t0
                        if (result.song != null) {
                            Log.e("LocalFolderMusicSource", "[${index + 1}/$total] '${result.song.title}' OK (${totalMs}ms)")

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
                            Log.w("LocalFolderMusicSource", "[${index + 1}/$total] ${uri.lastPathSegment} -> FAILED (${totalMs}ms)")
                        }
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }
                } finally {
                    try { afd.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w("LocalFolderMusicSource", "[${index + 1}/$total] ${uri.lastPathSegment} -> ERROR: ${e.message}")
            }
        }

        val albums = albumMap.values.toList()
        val allArtworks = albumArtworkMap.toMap()
        Log.e("LocalFolderMusicSource", "Scan complete: ${songs.size}/$total songs, ${albums.size} albums, ${allArtworks.values.sumOf { it.size }} artworks")

        LoadSongsResult(songs, albums, allArtworks)
    }

    private fun collectAudioFiles(dir: DocumentFile, result: MutableList<Uri>) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                collectAudioFiles(file, result)
            } else if (file.isFile && isAudioFile(file.name ?: "")) {
                result.add(file.uri)
            }
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val extensions = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus")
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in extensions
    }
}
