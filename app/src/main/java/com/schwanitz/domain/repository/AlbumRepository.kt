package com.schwanitz.domain.repository

import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork

interface AlbumRepository {
    suspend fun getAlbumArtworks(albumId: Long): List<AlbumArtwork>
    suspend fun getTrackTotal(albumId: Long, discNumber: Int): Int
    suspend fun getDiscTotal(albumId: Long): Int
    suspend fun findAlbumByNameAndArtist(name: String, albumArtist: String): Album?
}
