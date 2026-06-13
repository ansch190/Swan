package com.schwanitz.swan.data.local.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.schwanitz.swan.util.Logger
import androidx.documentfile.provider.DocumentFile
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.FilterEntity
import com.schwanitz.swan.data.local.entity.LibraryPathEntity
import com.schwanitz.swan.data.local.entity.MusicFileEntity
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.data.local.entity.toDomainModel
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.repository.MusicRepository
import com.schwanitz.swan.domain.repository.MusicRepository.ScanProgress
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

class MusicRepository private constructor(
    private val context: Context
) : MusicRepository {

    companion object {
        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MusicRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val db: AppDatabase by lazy { AppDatabase.getDatabase(context) }

    private val TAG = "MusicRepository"

    override suspend fun scanAndStoreMusicFiles(libraryPathUri: String, displayName: String): Flow<ScanProgress> = flow {
        Logger.d(TAG, "Scanning music files for URI: $libraryPathUri")
        try {
            db.libraryPathDao().insertPath(LibraryPathEntity(libraryPathUri, displayName))
            db.musicFileDao().deleteFilesByPath(libraryPathUri)
            Logger.d(TAG, "Deleted existing files for path: $libraryPathUri")

            val treeUri = Uri.parse(libraryPathUri)
            val initialDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

            val musicFiles = getMusicFiles(treeUri, initialDocumentId)
            val totalFiles = musicFiles.size
            Logger.d(TAG, "Found $totalFiles music files in $libraryPathUri")

            val batchSize = 100
            val batches = musicFiles.chunked(batchSize)
            var scannedFiles = 0

            batches.forEach { batch ->
                val batchEntities = supervisorScope {
                    batch.map { file ->
                        async(Dispatchers.IO) {
                            try {
                                val metadata = MetadataExtractor.getInstance(context).extractMetadata(file.uri)
                                Logger.d(TAG, "Extracted metadata for ${file.uri}: title=${metadata.title}, artist=${metadata.artist}, codec=${metadata.audioCodec}")
                                MusicFileEntity(
                                    uri = file.uri.toString(),
                                    libraryPathUri = libraryPathUri,
                                    name = file.name,
                                    title = metadata.title.takeIf { it.isNotEmpty() },
                                    artist = metadata.artist.takeIf { it.isNotEmpty() },
                                    album = metadata.album.takeIf { it.isNotEmpty() },
                                    albumArtist = metadata.albumArtist.takeIf { it.isNotEmpty() },
                                    discNumber = metadata.discNumber.takeIf { it.isNotEmpty() },
                                    trackNumber = metadata.trackNumber.takeIf { it.isNotEmpty() },
                                    year = metadata.year.takeIf { it != 0 },
                                    genre = metadata.genre.takeIf { it.isNotEmpty() },
                                    fileSize = metadata.fileSize,
                                    audioCodec = metadata.audioCodec.takeIf { it.isNotEmpty() },
                                    sampleRate = metadata.sampleRate,
                                    bitrate = metadata.bitrate,
                                    tagVersion = metadata.tagVersion.takeIf { it.isNotEmpty() }
                                )
                            } catch (e: Exception) {
                                Logger.e(TAG, "Error processing file: ${file.uri}", e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                db.musicFileDao().insertFiles(batchEntities)
                scannedFiles += batch.size
                emit(ScanProgress(scannedFiles, totalFiles))
                Logger.d(TAG, "Processed batch, scanned $scannedFiles/$totalFiles files")
            }

            Logger.d(TAG, "Stored ${scannedFiles} music files for path: $libraryPathUri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to scan and store music files for URI: $libraryPathUri, error: ${e.message}", e)
            throw e
        }
    }

    private fun getMusicFiles(treeUri: Uri, initialDocumentId: String): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()
        val stack = ArrayDeque<String>()
        stack.push(initialDocumentId)
        val seenDocumentIds = mutableSetOf<String>()

        while (stack.isNotEmpty()) {
            val currentDocumentId = stack.pop()
            if (seenDocumentIds.contains(currentDocumentId)) {
                continue
            }
            seenDocumentIds.add(currentDocumentId)

            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocumentId)
                Logger.d(TAG, "Processing directory documentId: $currentDocumentId, childrenUri: $childrenUri")

                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val childDocumentId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)
                        Logger.d(TAG, "Found item: documentId=$childDocumentId, displayName=$displayName, mimeType=$mimeType, fileUri=$fileUri")

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            stack.push(childDocumentId)
                        } else if (mimeType.startsWith("audio/")) {
                            musicFiles.add(
                                MusicFile(
                                    uri = fileUri,
                                    name = displayName,
                                    title = null,
                                    artist = null,
                                    album = null,
                                    albumArtist = null,
                                    discNumber = null,
                                    trackNumber = null,
                                    year = null,
                                    genre = null,
                                    fileSize = 0L,
                                    audioCodec = null,
                                    sampleRate = 0,
                                    bitrate = 0L,
                                    tagVersion = null
                                )
                            )
                            Logger.d(TAG, "Added music file: $displayName")
                        } else {
                            Logger.d(TAG, "Skipped non-audio item: $displayName, mimeType=$mimeType")
                        }
                    }
                } ?: Logger.e(TAG, "Query returned null cursor for URI: $childrenUri")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to query music files for documentId: $currentDocumentId, error: ${e.message}", e)
            }
        }
        return musicFiles.distinctBy { it.uri.toString() }
    }

    override suspend fun getDisplayName(uri: Uri): String {
        Logger.d(TAG, "Getting display name for URI: $uri")
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.name ?: uri.toString()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get display name for URI: $uri, error: ${e.message}", e)
            uri.toString()
        }
    }

    override fun observeAllMusicFiles(): Flow<List<MusicFile>> {
        return db.musicFileDao().getAllFiles().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getAllMusicFilesOnce(): List<MusicFile> {
        return db.musicFileDao().getAllFiles().first().map { it.toDomainModel() }
    }

    override suspend fun deleteFilesByPath(libraryPathUri: String) {
        db.musicFileDao().deleteFilesByPath(libraryPathUri)
    }

    override fun getAllFilters(): Flow<List<FilterEntity>> = db.filterDao().getAllFilters()

    override suspend fun getFiltersOnce(): List<FilterEntity> = db.filterDao().getAllFilters().first()

    override suspend fun insertFilter(filter: FilterEntity) = db.filterDao().insertFilter(filter)

    override suspend fun deleteFilter(criterion: String) = db.filterDao().deleteFilter(criterion)

    override fun getAllLibraryPaths(): Flow<List<LibraryPathEntity>> = db.libraryPathDao().getAllPaths()

    override suspend fun getLibraryPathsOnce(): List<LibraryPathEntity> = db.libraryPathDao().getAllPaths().first()

    override suspend fun deleteLibraryPath(uri: String) = db.libraryPathDao().deletePath(uri)

    override suspend fun getAllPlaylists(): List<PlaylistEntity> = db.playlistDao().getAllPlaylists().first()

    override suspend fun insertPlaylist(playlist: PlaylistEntity) = db.playlistDao().insertPlaylist(playlist)

    override suspend fun getSongsForPlaylist(playlistId: String): List<PlaylistSongEntity> =
        db.playlistDao().getSongsForPlaylist(playlistId)

    override suspend fun insertPlaylistSongs(songs: List<PlaylistSongEntity>) =
        db.playlistDao().insertPlaylistSongs(songs)

    override suspend fun deletePlaylist(playlistId: String) = db.playlistDao().deletePlaylist(playlistId)

    override suspend fun getPlaylistById(playlistId: String): PlaylistEntity? =
        db.playlistDao().getPlaylistById(playlistId)

    override suspend fun updatePlaylist(playlist: PlaylistEntity) = db.playlistDao().updatePlaylist(playlist)

    override suspend fun deletePlaylistSong(songId: String) = db.playlistDao().deletePlaylistSong(songId)

    override fun getPlaylistsFlow(): Flow<List<PlaylistEntity>> = db.playlistDao().getAllPlaylists()

    override suspend fun getFileByUri(uri: String): MusicFile? {
        return db.musicFileDao().getFileByUri(uri).first()?.toDomainModel()
    }

    override suspend fun insertLibraryPath(path: LibraryPathEntity) = db.libraryPathDao().insertPath(path)
}
