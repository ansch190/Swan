package com.schwanitz.data.local.converter

import com.schwanitz.data.local.entity.SongWithNames
import com.schwanitz.data.local.entity.AlbumArtworkEntity
import com.schwanitz.data.local.entity.AlbumEntity
import com.schwanitz.data.local.entity.AlbumSongMappingEntity
import com.schwanitz.data.local.entity.SongEntity
import com.schwanitz.data.local.entity.SongTechnicalInfoEntity
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapperTest {

    private fun songWithNames(
        id: String = "song-1",
        title: String = "Title",
        artistId: Long? = 1L,
        artistName: String? = "Artist",
        albumId: Long? = 10L,
        albumName: String? = "Album",
        albumArtistName: String? = "Album Artist",
        durationMs: Long = 300_000,
        albumArtUri: String? = "art_small.jpg",
        albumArtUriLarge: String? = "art_large.jpg",
        sourceId: String = "local",
        isFavorite: Boolean = false,
        isActive: Boolean = true,
        discNumber: Int = 1,
        trackNumber: Int = 5,
        year: Int = 2024,
        genre: String = "Rock",
        mimeType: String = "audio/mpeg",
        sampleRate: Int = 44100,
        bitrate: Int = 320,
        fileSize: Long = 10_000_000,
        tagVersion: String = "ID3v2.3"
    ) = SongWithNames(
        id = id, title = title, artistId = artistId, artistName = artistName,
        albumId = albumId, albumName = albumName, albumArtistName = albumArtistName,
        durationMs = durationMs, albumArtUri = albumArtUri, albumArtUriLarge = albumArtUriLarge,
        sourceId = sourceId, isFavorite = isFavorite, isActive = isActive,
        discNumber = discNumber, trackNumber = trackNumber, year = year,
        genre = genre, mimeType = mimeType, sampleRate = sampleRate, bitrate = bitrate,
        fileSize = fileSize, tagVersion = tagVersion
    )

    @Test
    fun `SongWithNames toDomain maps all fields`() {
        val song = songWithNames().toDomain()
        assertEquals("song-1", song.id)
        assertEquals("Title", song.title)
        assertEquals(1L, song.artistId)
        assertEquals("Artist", song.artistName)
        assertEquals(10L, song.albumId)
        assertEquals("Album", song.albumName)
        assertEquals("Album Artist", song.albumArtistName)
        assertEquals(300_000L, song.durationMs)
        assertEquals("art_small.jpg", song.albumArtUri)
        assertEquals("art_large.jpg", song.albumArtUriLarge)
        assertEquals("local", song.sourceId)
        assertEquals(false, song.isFavorite)
        assertEquals(true, song.isActive)
        assertEquals(1, song.discNumber)
        assertEquals(5, song.trackNumber)
        assertEquals(2024, song.year)
        assertEquals("Rock", song.genre)
        assertEquals("audio/mpeg", song.mimeType)
        assertEquals(44100, song.sampleRate)
        assertEquals(320, song.bitrate)
        assertEquals(10_000_000L, song.fileSize)
        assertEquals("ID3v2.3", song.tagVersion)
    }

    @Test
    fun `SongWithNames toDomain handles null artistName as empty string`() {
        val song = songWithNames(artistName = null).toDomain()
        assertEquals("", song.artistName)
    }

    @Test
    fun `SongWithNames toDomain handles null albumName as empty string`() {
        val song = songWithNames(albumName = null).toDomain()
        assertEquals("", song.albumName)
    }

    @Test
    fun `SongWithNames toDomain handles null albumArtistName as empty string`() {
        val song = songWithNames(albumArtistName = null).toDomain()
        assertEquals("", song.albumArtistName)
    }

    @Test
    fun `Song toEntity maps relevant fields`() {
        val domain = Song(
            id = "s1", title = "T", artistId = 2L, artistName = "A",
            albumId = 3L, albumName = "B", durationMs = 100_000,
            sourceId = "src", isFavorite = true, isActive = false,
            genre = "Jazz", tagVersion = "v1",
            discNumber = 1, trackNumber = 2, year = 2020,
            mimeType = "audio/flac", sampleRate = 96000,
            bitrate = 1411, fileSize = 50_000_000
        )
        val entity = domain.toEntity()
        assertEquals("s1", entity.id)
        assertEquals("T", entity.title)
        assertEquals(2L, entity.artistId)
        assertEquals(100_000L, entity.durationMs)
        assertEquals("src", entity.sourceId)
        assertEquals(true, entity.isFavorite)
        assertEquals(false, entity.isActive)
        assertEquals("Jazz", entity.genre)
        assertEquals("v1", entity.tagVersion)
    }

    @Test
    fun `Song toMappingEntity maps songId albumId trackNumber discNumber`() {
        val song = Song(
            id = "s1", title = "T", durationMs = 0, sourceId = "src",
            trackNumber = 7, discNumber = 2
        )
        val mapping = song.toMappingEntity(albumId = 42L)
        assertEquals("s1", mapping.songId)
        assertEquals(42L, mapping.albumId)
        assertEquals(7, mapping.trackNumber)
        assertEquals(2, mapping.discNumber)
    }

    @Test
    fun `Song toTechnicalInfoEntity maps technical fields`() {
        val song = Song(
            id = "s1", title = "T", durationMs = 0, sourceId = "src",
            fileSize = 99_000, bitrate = 320, sampleRate = 44100, mimeType = "audio/mp3"
        )
        val tech = song.toTechnicalInfoEntity()
        assertEquals("s1", tech.songId)
        assertEquals(99_000L, tech.fileSize)
        assertEquals(320, tech.bitrate)
        assertEquals(44100, tech.sampleRate)
        assertEquals("audio/mp3", tech.mimeType)
    }

    @Test
    fun `Album toEntity maps all fields`() {
        val album = Album(id = 5L, name = "Test Album", albumArtist = "Artist A", year = 2023)
        val entity = album.toEntity()
        assertEquals(5L, entity.id)
        assertEquals("Test Album", entity.name)
        assertEquals("Artist A", entity.albumArtist)
        assertEquals(2023, entity.year)
    }

    @Test
    fun `AlbumArtwork roundtrip preserves identity`() {
        val original = AlbumArtwork(albumId = 10L, sortOrder = 0, uriLarge = "large.jpg", uriSmall = "small.jpg")
        val roundtripped = original.toEntity().toDomain()
        assertEquals(original, roundtripped)
    }

    @Test
    fun `AlbumArtworkEntity toDomain maps all fields`() {
        val entity = AlbumArtworkEntity(albumId = 7L, sortOrder = 2, uriLarge = "big.png", uriSmall = "sm.png")
        val domain = entity.toDomain()
        assertEquals(7L, domain.albumId)
        assertEquals(2, domain.sortOrder)
        assertEquals("big.png", domain.uriLarge)
        assertEquals("sm.png", domain.uriSmall)
    }

    @Test
    fun `AlbumArtwork null uriSmall preserved`() {
        val entity = AlbumArtworkEntity(albumId = 1L, sortOrder = 0, uriLarge = "l.jpg")
        val domain = entity.toDomain()
        assertNull(domain.uriSmall)
    }
}
