package com.schwanitz.swan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

class MusicRepository(private val context: Context) {

    private val TAG = "MusicRepository"

    data class ScanProgress(val scannedFiles: Int, val totalFiles: Int)

    suspend fun scanAndStoreMusicFiles(libraryPathUri: String, db: AppDatabase): Flow<ScanProgress> = flow {
        Log.d(TAG, "Scanning music files for URI: $libraryPathUri")
        try {
            db.musicFileDao().deleteFilesByPath(libraryPathUri)
            Log.d(TAG, "Deleted existing files for path: $libraryPathUri")

            val treeUri = Uri.parse(libraryPathUri)
            val initialDocumentId = DocumentsContract.getTreeDocumentId(treeUri)

            // Zähle zunächst alle Musikdateien
            val musicFiles = getMusicFiles(treeUri, initialDocumentId)
            val totalFiles = musicFiles.size
            Log.d(TAG, "Found $totalFiles music files in $libraryPathUri")

            val batchSize = 100 // Batch-Größe anpassen, je nach Bedarf
            val batches = musicFiles.chunked(batchSize)
            var scannedFiles = 0

            batches.forEach { batch ->
                val batchEntities = supervisorScope {
                    batch.map { file ->
                        async(Dispatchers.IO) {
                            try {
                                val metadata = MetadataExtractor(context).extractMetadata(file.uri)
                                Log.d(TAG, "Extracted metadata for ${file.uri}: title=${metadata.title}, artist=${metadata.artist}, codec=${metadata.audioCodec}")
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
                                    year = metadata.year.takeIf { it.isNotEmpty() },
                                    genre = metadata.genre.takeIf { it.isNotEmpty() },
                                    fileSize = metadata.fileSize,
                                    audioCodec = metadata.audioCodec.takeIf { it.isNotEmpty() },
                                    sampleRate = metadata.sampleRate,
                                    bitrate = metadata.bitrate,
                                    tagVersion = metadata.tagVersion.takeIf { it.isNotEmpty() }
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing file: ${file.uri}", e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

                db.musicFileDao().insertFiles(batchEntities)
                scannedFiles += batch.size
                emit(ScanProgress(scannedFiles, totalFiles))
                Log.d(TAG, "Processed batch, scanned $scannedFiles/$totalFiles files")
            }

            Log.d(TAG, "Stored ${scannedFiles} music files for path: $libraryPathUri")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan and store music files for URI: $libraryPathUri, error: ${e.message}", e)
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
                Log.d(TAG, "Processing directory documentId: $currentDocumentId, childrenUri: $childrenUri")

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
                        Log.d(TAG, "Found item: documentId=$childDocumentId, displayName=$displayName, mimeType=$mimeType, fileUri=$fileUri")

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
                                    fileSize = 0,
                                    audioCodec = null,
                                    sampleRate = 0,
                                    bitrate = 0L,
                                    tagVersion = null
                                )
                            )
                            Log.d(TAG, "Added music file: $displayName")
                        } else {
                            Log.d(TAG, "Skipped non-audio item: $displayName, mimeType=$mimeType")
                        }
                    }
                } ?: Log.e(TAG, "Query returned null cursor for URI: $childrenUri")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query music files for documentId: $currentDocumentId, error: ${e.message}", e)
            }
        }
        return musicFiles.distinctBy { it.uri.toString() }
    }

    suspend fun getDisplayName(uri: Uri): String {
        Log.d(TAG, "Getting display name for URI: $uri")
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.name ?: uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get display name for URI: $uri, error: ${e.message}", e)
            uri.toString()
        }
    }
}