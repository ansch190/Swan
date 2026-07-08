package com.schwanitz.data.local.converter

import com.schwanitz.data.local.entity.*
import com.schwanitz.domain.model.ArtistImage
import com.schwanitz.domain.model.ArtistProfile
import com.schwanitz.domain.model.Playlist
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    albumArtUri = albumArtUri,
    sourceId = sourceId,
    isFavorite = isFavorite,
    isActive = isActive,
    albumArtist = albumArtist,
    discNumber = discNumber,
    trackNumber = trackNumber,
    trackRaw = trackRaw,
    discRaw = discRaw,
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
    artist = artist,
    album = album,
    durationMs = durationMs,
    albumArtUri = albumArtUri,
    sourceId = sourceId,
    isFavorite = isFavorite,
    isActive = isActive,
    albumArtist = albumArtist,
    discNumber = discNumber,
    trackNumber = trackNumber,
    trackRaw = trackRaw,
    discRaw = discRaw,
    year = year,
    genre = genre,
    mimeType = mimeType,
    sampleRate = sampleRate,
    bitrate = bitrate,
    fileSize = fileSize,
    tagVersion = tagVersion
)

fun PlaylistWithSongs.toDomain(): Playlist = Playlist(
    id = playlist.id,
    name = playlist.name,
    description = playlist.description,
    createdAt = playlist.createdAt,
    songs = songs.map { it.toDomain() }
)

fun PlaylistEntity.toDomain(songs: List<SongEntity> = emptyList()): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt,
    songs = songs.map { it.toDomain() }
)

fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    createdAt = createdAt
)

fun SongArtworkEntity.toDomain(): SongArtwork = SongArtwork(
    songId = songId,
    sortOrder = sortOrder,
    pictureType = pictureType,
    uri = uri
)

fun SongArtwork.toEntity(): SongArtworkEntity = SongArtworkEntity(
    songId = songId,
    sortOrder = sortOrder,
    pictureType = pictureType,
    uri = uri
)

fun ArtistImageEntity.toDomain(): ArtistImage = ArtistImage(
    artistName = artistName,
    discogsArtistId = discogsArtistId,
    imageUrl = imageUrl,
    localUri = localUri,
    lastUpdated = lastUpdated
)

fun ArtistProfileEntity.toDomain(): ArtistProfile = ArtistProfile(
    artistName = artistName,
    summary = summary,
    content = profile,
    source = source,
    lastUpdated = lastUpdated
)

fun ArtistProfile.toEntity(): ArtistProfileEntity = ArtistProfileEntity(
    artistName = artistName,
    profile = content,
    summary = summary,
    source = source,
    lastUpdated = lastUpdated
)
