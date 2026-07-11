package com.schwanitz.data.repository

import com.schwanitz.data.local.dao.PlaylistDao
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongMapping
import com.schwanitz.data.local.converter.toSongDomain
import com.schwanitz.domain.model.Playlist
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao
) : PlaylistRepository {

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylistsWithCount().map { list ->
            list.map { Playlist(id = it.playlist.id, name = it.playlist.name) }
        }
    }

    override fun getAllPlaylistSongCounts(): Flow<Map<Long, Int>> {
        return playlistDao.getAllPlaylistsWithCount().map { list ->
            list.associate { it.playlist.id to it.songCount }
        }
    }

    override fun getPlaylistName(playlistId: Long): Flow<String?> {
        return playlistDao.getPlaylistName(playlistId)
    }

    override fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return playlistDao.getPlaylistSongsOrdered(playlistId).map { list ->
            list.map { it.toSongDomain() }
        }
    }

    override suspend fun createPlaylist(name: String): Long {
        val entity = PlaylistEntity(name = name)
        return playlistDao.createPlaylist(entity)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(PlaylistEntity(id = playlistId, name = ""))
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistDao.rename(playlistId, newName)
    }

    override suspend fun getPlaylistSongCount(playlistId: Long): Int {
        return playlistDao.getPlaylistSongCount(playlistId)
    }

    override suspend fun addSongToPlaylist(playlistId: Long, songId: String, order: Int) {
        val crossRef = PlaylistSongMapping(
            playlistId = playlistId,
            songId = songId,
            orderIndex = order
        )
        playlistDao.addSongToPlaylist(crossRef)
    }

    override suspend fun reorderSongs(playlistId: Long, songIds: List<String>) {
        playlistDao.reorderSongs(playlistId, songIds)
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        val crossRef = PlaylistSongMapping(
            playlistId = playlistId,
            songId = songId,
            orderIndex = 0
        )
        playlistDao.removeSongFromPlaylist(crossRef)
    }
}
