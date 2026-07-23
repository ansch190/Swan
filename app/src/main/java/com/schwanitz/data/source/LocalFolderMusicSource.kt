package com.schwanitz.data.source

import android.content.Context
import android.net.Uri
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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalFolderMusicSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MusicSource {

    override val type: SourceType = SourceType.LOCAL

    override suspend fun loadSongs(
        config: SourceConfig,
        onProgress: (Int, Int) -> Unit,
        onBatch: suspend (LoadSongsResult) -> Unit
    ): LoadSongsResult = withContext(Dispatchers.IO) {
        val treeUri = Uri.parse(config.folderUri ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap()))
        val documentFile = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext LoadSongsResult(emptyList(), emptyList(), emptyMap())

        val audioUris = mutableListOf<Uri>()
        collectAudioFiles(documentFile, audioUris)

        val total = audioUris.size
        val songs = mutableListOf<Song>()
        val albumMap = mutableMapOf<String, Album>()
        val albumArtworkMap = mutableMapOf<String, MutableList<AlbumArtwork>>()
        val albumArtworkCache = mutableMapOf<String, List<ArtworkResult>>()

        Timber.d("Starting scan: %d files", total)
        audioUris.forEachIndexed { index, uri ->
            val t0 = System.currentTimeMillis()
            onProgress(index + 1, total)

            try {
                val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: run {
                        Timber.w("[%d/%d] Cannot open %s", index + 1, total, uri)
                        return@forEachIndexed
                    }
                try {
                    val source = ContentUriDataSource(afd, uri.toString())
                    val fileSize = source.length()
                    val extension = uri.toString().substringAfterLast('.', "").lowercase()
                    val result = MetadataExtractor.buildSong(
                        source, uri.toString(), config.id, context, fileSize,
                        albumArtworkCache = albumArtworkCache,
                        fileExtension = extension
                    )
                    val totalMs = System.currentTimeMillis() - t0
                    if (result.song != null) {
                        Timber.d("[%d/%d] '%s' OK (%dms)", index + 1, total, result.song.title, totalMs)

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

                        if (result.artworks.isNotEmpty() && albumKey !in albumArtworkMap) {
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
                        Timber.w("[%d/%d] %s -> FAILED (%dms)", index + 1, total, uri.lastPathSegment, totalMs)
                    }
                } finally {
                    try { afd.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Timber.w(e, "[%d/%d] %s -> ERROR", index + 1, total, uri.lastPathSegment)
            }
        }

        val albums = albumMap.values.toList()
        val allArtworks = albumArtworkMap.toMap()
        Timber.d("Scan complete: %d/%d songs, %d albums, %d artworks", songs.size, total, albums.size, allArtworks.values.sumOf { it.size })

        LoadSongsResult(songs, albums, allArtworks)
    }

    private fun collectAudioFiles(dir: DocumentFile, result: MutableList<Uri>) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                collectAudioFiles(file, result)
            } else if (file.isFile && (file.name ?: "").isAudioFile()) {
                result.add(file.uri)
            }
        }
    }
}
