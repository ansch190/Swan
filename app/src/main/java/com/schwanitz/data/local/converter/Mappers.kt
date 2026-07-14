package com.schwanitz.data.local.converter

import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.entity.*
import com.schwanitz.domain.model.*

fun SongWithNames.toDomain(): Song = Song(
    id = id,
    title = title,
    artistId = artistId,
    artistName = artistName ?: "",
    albumId = albumId,
    albumName = albumName ?: "",
    albumArtistName = albumArtistName ?: "",
    durationMs = durationMs,
    albumArtUri = albumArtUri,
    albumArtUriLarge = albumArtUriLarge,
    sourceId = sourceId,
    isFavorite = isFavorite,
    isActive = isActive,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    mimeType = mimeType,
    sampleRate = sampleRate,
    bitrate = bitrate,
    fileSize = fileSize,
    tagVersion = tagVersion
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artistId = artistId,
    durationMs = durationMs,
    sourceId = sourceId,
    isFavorite = isFavorite,
    isActive = isActive,
    genre = genre,
    tagVersion = tagVersion
)

fun Song.toMappingEntity(albumId: Long): AlbumSongMappingEntity = AlbumSongMappingEntity(
    songId = id,
    albumId = albumId,
    trackNumber = trackNumber,
    discNumber = discNumber
)

fun Song.toTechnicalInfoEntity(): SongTechnicalInfoEntity = SongTechnicalInfoEntity(
    songId = id,
    fileSize = fileSize,
    bitrate = bitrate,
    sampleRate = sampleRate,
    mimeType = mimeType
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    name = name,
    albumArtist = albumArtist,
    year = year
)

fun SongDao.AlbumProjection.toDomain(): Album = Album(
    id = albumId,
    name = albumName,
    albumArtist = albumArtist ?: "",
    albumArtUri = albumArtUri
)

fun AlbumArtworkEntity.toDomain(): AlbumArtwork = AlbumArtwork(
    albumId = albumId,
    sortOrder = sortOrder,
    uriLarge = uriLarge,
    uriSmall = uriSmall
)

fun AlbumArtwork.toEntity(): AlbumArtworkEntity = AlbumArtworkEntity(
    albumId = albumId,
    sortOrder = sortOrder,
    uriLarge = uriLarge,
    uriSmall = uriSmall
)
