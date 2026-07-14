package com.schwanitz.data.repository

import com.schwanitz.data.local.dao.AlbumDao
import com.schwanitz.data.local.dao.AlbumArtworkDao
import com.schwanitz.data.local.dao.AlbumSongDao
import com.schwanitz.data.local.converter.toDomain
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.repository.AlbumRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val albumArtworkDao: AlbumArtworkDao,
    private val albumSongDao: AlbumSongDao
) : AlbumRepository {

    override suspend fun getAlbumArtworks(albumId: Long): List<AlbumArtwork> {
        return albumArtworkDao.getForAlbum(albumId).map { it.toDomain() }
    }

    override suspend fun getTrackTotal(albumId: Long, discNumber: Int): Int {
        return albumSongDao.getTrackTotal(albumId, discNumber)
    }

    override suspend fun getDiscTotal(albumId: Long): Int {
        return albumSongDao.getDiscTotal(albumId)
    }

    override suspend fun findAlbumByNameAndArtist(name: String, albumArtist: String): Album? {
        return albumDao.findByNameAndAlbumArtist(name, albumArtist)?.let {
            Album(id = it.id, name = it.name, albumArtist = it.albumArtist, year = it.year)
        }
    }
}
