package com.schwanitz.swan.domain.repository

import android.net.Uri
import com.schwanitz.swan.data.local.entity.FilterEntity
import com.schwanitz.swan.data.local.entity.LibraryPathEntity
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.domain.model.MusicFile
import kotlinx.coroutines.flow.Flow

interface MusicRepository {

    data class ScanProgress(val scannedFiles: Int, val totalFiles: Int)

    suspend fun scanAndStoreMusicFiles(libraryPathUri: String, displayName: String): Flow<ScanProgress>

    suspend fun getDisplayName(uri: Uri): String

    fun observeAllMusicFiles(): Flow<List<MusicFile>>

    suspend fun getAllMusicFilesOnce(): List<MusicFile>

    suspend fun deleteFilesByPath(libraryPathUri: String)

    fun getAllFilters(): Flow<List<FilterEntity>>

    suspend fun getFiltersOnce(): List<FilterEntity>

    suspend fun insertFilter(filter: FilterEntity)

    suspend fun deleteFilter(criterion: String)

    fun getAllLibraryPaths(): Flow<List<LibraryPathEntity>>

    suspend fun getLibraryPathsOnce(): List<LibraryPathEntity>

    suspend fun deleteLibraryPath(uri: String)

    suspend fun getAllPlaylists(): List<PlaylistEntity>

    suspend fun insertPlaylist(playlist: PlaylistEntity)

    suspend fun getSongsForPlaylist(playlistId: String): List<PlaylistSongEntity>

    suspend fun insertPlaylistSongs(songs: List<PlaylistSongEntity>)

    suspend fun deletePlaylist(playlistId: String)

    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    suspend fun updatePlaylist(playlist: PlaylistEntity)

    suspend fun deletePlaylistSong(songId: String)

    fun getPlaylistsFlow(): Flow<List<PlaylistEntity>>

    suspend fun getFileByUri(uri: String): MusicFile?

    suspend fun insertLibraryPath(path: LibraryPathEntity)
}
