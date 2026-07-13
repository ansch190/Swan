package com.schwanitz.ui.screens.playlist

import com.schwanitz.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistExporterTest {

    private fun testSong(
        id: String = "/music/song.mp3",
        title: String = "Test Song",
        artistName: String = "Test Artist",
        albumName: String = "Test Album",
        durationMs: Long = 240_000L
    ) = Song(
        id = id,
        title = title,
        artistName = artistName,
        albumName = albumName,
        durationMs = durationMs,
        sourceId = "local"
    )

    @Test
    fun `escapeXml escapes ampersand`() {
        assertEquals("&amp;", PlaylistExporter.escapeXml("&"))
    }

    @Test
    fun `escapeXml escapes angle brackets`() {
        assertEquals("&lt;", PlaylistExporter.escapeXml("<"))
        assertEquals("&gt;", PlaylistExporter.escapeXml(">"))
    }

    @Test
    fun `escapeXml escapes quotes`() {
        assertEquals("&quot;", PlaylistExporter.escapeXml("\""))
    }

    @Test
    fun `escapeXml escapes apostrophe`() {
        assertEquals("&apos;", PlaylistExporter.escapeXml("'"))
    }

    @Test
    fun `escapeXml escapes multiple special chars`() {
        assertEquals("A &amp; B &lt; C &gt; D", PlaylistExporter.escapeXml("A & B < C > D"))
    }

    @Test
    fun `escapeXml leaves normal text unchanged`() {
        assertEquals("Hello World", PlaylistExporter.escapeXml("Hello World"))
    }

    @Test
    fun `buildM3u produces correct header`() {
        val output = PlaylistExporter.buildM3u(emptyList())
        assertTrue(output.startsWith("#EXTM3U"))
    }

    @Test
    fun `buildM3u with songs contains EXTINF and paths`() {
        val songs = listOf(testSong(id="/a.mp3", title="Song A", artistName="Artist A", durationMs=180_000))
        val output = PlaylistExporter.buildM3u(songs)
        assertTrue(output.contains("#EXTINF:180,Artist A - Song A"))
        assertTrue(output.contains("/a.mp3"))
    }

    @Test
    fun `buildM3u without artist uses title only`() {
        val songs = listOf(testSong(title="Solo Track", artistName=""))
        val output = PlaylistExporter.buildM3u(songs)
        assertTrue(output.contains("#EXTINF:240,Solo Track"))
    }

    @Test
    fun `buildM3u duration is in seconds`() {
        val songs = listOf(testSong(durationMs=65_000))
        val output = PlaylistExporter.buildM3u(songs)
        assertTrue(output.contains("#EXTINF:65,"))
    }

    @Test
    fun `buildPls contains NumberOfEntries and Version`() {
        val songs = listOf(testSong(), testSong(id="/b.mp3", title="Song B"))
        val output = PlaylistExporter.buildPls(songs)
        assertTrue(output.contains("[playlist]"))
        assertTrue(output.contains("NumberOfEntries=2"))
        assertTrue(output.contains("Version=2"))
    }

    @Test
    fun `buildPls entries have File Title Length`() {
        val songs = listOf(testSong(id="/x.mp3", title="My Song", durationMs=300_000))
        val output = PlaylistExporter.buildPls(songs)
        assertTrue(output.contains("File1=/x.mp3"))
        assertTrue(output.contains("Title1=My Song"))
        assertTrue(output.contains("Length1=300"))
    }

    @Test
    fun `buildXspf contains XML header and playlist tag`() {
        val output = PlaylistExporter.buildXspf("My List", emptyList())
        assertTrue(output.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(output.contains("<playlist version=\"1\""))
        assertTrue(output.contains("<title>My List</title>"))
    }

    @Test
    fun `buildXspf with songs contains track elements`() {
        val songs = listOf(testSong(title="Track 1", artistName="Artist 1", albumName="Album 1", durationMs=200_000))
        val output = PlaylistExporter.buildXspf("List", songs)
        assertTrue(output.contains("<title>Track 1</title>"))
        assertTrue(output.contains("<creator>Artist 1</creator>"))
        assertTrue(output.contains("<album>Album 1</album>"))
        assertTrue(output.contains("<duration>200000</duration>"))
    }

    @Test
    fun `buildXspf omits blank artist and album`() {
        val songs = listOf(testSong(artistName="", albumName=""))
        val output = PlaylistExporter.buildXspf("List", songs)
        assertTrue(output.contains("<title>"))
        assertTrue(!output.contains("<creator>"))
        assertTrue(!output.contains("<album>"))
    }

    @Test
    fun `buildXspf escapes XML in names`() {
        val songs = listOf(testSong(title="Rock & Roll"))
        val output = PlaylistExporter.buildXspf("A < B", songs)
        assertTrue(output.contains("Rock &amp; Roll"))
        assertTrue(output.contains("A &lt; B"))
    }

    @Test
    fun `buildWpl contains wpl header and smil structure`() {
        val output = PlaylistExporter.buildWpl("WPL List", emptyList())
        assertTrue(output.contains("<?wpl version=\"1.0\"?>"))
        assertTrue(output.contains("<smil>"))
        assertTrue(output.contains("<title>WPL List</title>"))
    }

    @Test
    fun `buildWpl with songs contains media elements`() {
        val songs = listOf(testSong(id="/track.mp3"))
        val output = PlaylistExporter.buildWpl("List", songs)
        assertTrue(output.contains("<media src=\"/track.mp3\"/>"))
    }

    @Test
    fun `buildAsx contains asx header`() {
        val output = PlaylistExporter.buildAsx("ASX List", emptyList())
        assertTrue(output.contains("<asx version=\"3.0\">"))
        assertTrue(output.contains("<title>ASX List</title>"))
    }

    @Test
    fun `buildAsx with songs contains entry elements`() {
        val songs = listOf(testSong(title="Asx Song", artistName="Asx Artist"))
        val output = PlaylistExporter.buildAsx("List", songs)
        assertTrue(output.contains("<title>Asx Song</title>"))
        assertTrue(output.contains("<author>Asx Artist</author>"))
        assertTrue(output.contains("<ref href="))
    }

    @Test
    fun `buildAsx omits blank artist`() {
        val songs = listOf(testSong(artistName=""))
        val output = PlaylistExporter.buildAsx("List", songs)
        assertTrue(!output.contains("<author>"))
    }

    @Test
    fun `buildB4s contains playlist tag`() {
        val output = PlaylistExporter.buildB4s(emptyList())
        assertTrue(output.contains("<playlist>"))
    }

    @Test
    fun `buildB4s with titled song has entry with name and length`() {
        val songs = listOf(testSong(title="B4S Song", durationMs=180_000))
        val output = PlaylistExporter.buildB4s(songs)
        assertTrue(output.contains("<entry playstring="))
        assertTrue(output.contains("<name>B4S Song</name>"))
        assertTrue(output.contains("<length>180</length>"))
    }

    @Test
    fun `buildB4s with untitled song has self-closing entry`() {
        val songs = listOf(testSong(title=""))
        val output = PlaylistExporter.buildB4s(songs)
        assertTrue(output.contains("<entry playstring="))
        assertTrue(!output.contains("<name>"))
    }

    @Test
    fun `buildB4s escapes path in playstring`() {
        val songs = listOf(testSong(id="file with & spaces.mp3"))
        val output = PlaylistExporter.buildB4s(songs)
        assertTrue(output.contains("file with &amp; spaces.mp3"))
    }

    @Test
    fun `all formats handle empty song list`() {
        val m3u = PlaylistExporter.buildM3u(emptyList())
        val pls = PlaylistExporter.buildPls(emptyList())
        val xspf = PlaylistExporter.buildXspf("Empty", emptyList())
        val wpl = PlaylistExporter.buildWpl("Empty", emptyList())
        val asx = PlaylistExporter.buildAsx("Empty", emptyList())
        val b4s = PlaylistExporter.buildB4s(emptyList())
        assertTrue(m3u.contains("#EXTM3U"))
        assertTrue(pls.contains("NumberOfEntries=0"))
        assertTrue(xspf.contains("<trackList>"))
        assertTrue(wpl.contains("<seq>"))
        assertTrue(asx.contains("<asx"))
        assertTrue(b4s.contains("<playlist>"))
    }

    @Test
    fun `parseDocumentId converts primary volume`() {
        assertEquals("/storage/emulated/0/Music/song.mp3", PlaylistExporter.parseDocumentId("primary:Music/song.mp3"))
    }

    @Test
    fun `parseDocumentId converts other volumes`() {
        assertEquals("/storage/0A1B2C3D/Music/song.mp3", PlaylistExporter.parseDocumentId("0A1B2C3D:Music/song.mp3"))
    }

    @Test
    fun `parseDocumentId handles empty path`() {
        assertEquals("/storage/emulated/0/", PlaylistExporter.parseDocumentId("primary:"))
    }

    @Test
    fun `parseDocumentId handles volume-only`() {
        assertEquals("/storage/emulated/0/", PlaylistExporter.parseDocumentId("primary"))
    }

    @Test
    fun `prepareSongPaths leaves non-content URIs unchanged`() {
        val song = testSong(id="/storage/emulated/0/Music/song.mp3")
        val result = PlaylistExporter.prepareSongPaths(listOf(song))
        assertEquals("/storage/emulated/0/Music/song.mp3", result[0].id)
    }
}
