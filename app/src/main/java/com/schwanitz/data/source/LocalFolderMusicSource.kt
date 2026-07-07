package com.schwanitz.data.source

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork
import com.schwanitz.domain.source.LoadSongsResult
import com.schwanitz.domain.source.MusicSource
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
        val treeUri = Uri.parse(config.folderUri ?: return@withContext LoadSongsResult(emptyList(), emptyList()))
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext LoadSongsResult(emptyList(), emptyList())

        val audioUris = mutableListOf<Uri>()
        collectAudioFiles(documentFile, audioUris)

        val total = audioUris.size
        val songs = mutableListOf<Song>()
        val allArtworks = mutableListOf<SongArtwork>()

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
                            source, retriever, uri.toString(), config.id, context, fileSize
                        )
                        val totalMs = System.currentTimeMillis() - t0
                        if (result.song != null) {
                            Log.e("LocalFolderMusicSource", "[${index + 1}/$total] '${result.song.title}' OK (${totalMs}ms)")
                            songs.add(result.song)
                            result.artworkUris.forEachIndexed { artIndex, artUri ->
                                allArtworks.add(
                                    SongArtwork(
                                        songId = uri.toString(),
                                        sortOrder = artIndex,
                                        pictureType = "Front Cover",
                                        uri = artUri
                                    )
                                )
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
        Log.e("LocalFolderMusicSource", "Scan complete: ${songs.size}/$total songs, ${allArtworks.size} artworks")

        LoadSongsResult(songs, allArtworks)
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
