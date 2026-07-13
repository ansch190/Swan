package com.schwanitz.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesDetectorTest {

    @Test
    fun `Vol pattern detects series`() {
        val albums = setOf("Greatest Hits Vol. 1", "Greatest Hits Vol. 2", "Greatest Hits Vol. 3")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("Greatest Hits", result[0].seriesName)
        assertEquals(3, result[0].volumes.size)
        assertEquals(1, result[0].volumes[0].volumeNumber)
        assertEquals(2, result[0].volumes[1].volumeNumber)
        assertEquals(3, result[0].volumes[2].volumeNumber)
    }

    @Test
    fun `Volume pattern detects series`() {
        val albums = setOf("Jazz Collection Volume 1", "Jazz Collection Volume 2")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("Jazz Collection", result[0].seriesName)
        assertEquals(2, result[0].volumes.size)
    }

    @Test
    fun `Part pattern detects series`() {
        val albums = setOf("Rock Anthems Part 1", "Rock Anthems Part 2", "Rock Anthems Part 3")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("Rock Anthems", result[0].seriesName)
        assertEquals(3, result[0].volumes.size)
    }

    @Test
    fun `Hash pattern detects series`() {
        val albums = setOf("Live Sessions #1", "Live Sessions #2")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("Live Sessions", result[0].seriesName)
        assertEquals(2, result[0].volumes.size)
    }

    @Test
    fun `Plain number pattern detects series`() {
        val albums = setOf("Ambient Works 1", "Ambient Works 2", "Ambient Works 3")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("Ambient Works", result[0].seriesName)
        assertEquals(3, result[0].volumes.size)
    }

    @Test
    fun `single album does not form series`() {
        val albums = setOf("Greatest Hits Vol. 1")
        val result = SeriesDetector.detectSeries(albums)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `volumes are sorted by number`() {
        val albums = setOf("Collection Vol. 3", "Collection Vol. 1", "Collection Vol. 2")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals(listOf(1, 2, 3), result[0].volumes.map { it.volumeNumber })
    }

    @Test
    fun `results are sorted by series name`() {
        val albums = setOf(
            "Zeta Vol. 1", "Zeta Vol. 2",
            "Alpha Vol. 1", "Alpha Vol. 2"
        )
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(2, result.size)
        assertEquals("Alpha", result[0].seriesName)
        assertEquals("Zeta", result[1].seriesName)
    }

    @Test
    fun `case insensitive matching`() {
        val albums = setOf("My Series vol. 1", "My Series VOL. 2", "My Series Vol. 3")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("My Series", result[0].seriesName)
    }

    @Test
    fun `different patterns do not mix`() {
        val albums = setOf(
            "Rock Vol. 1", "Rock Vol. 2",
            "Jazz Part 1", "Jazz Part 2"
        )
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(2, result.size)
        val names = result.map { it.seriesName }.toSet()
        assertTrue(names.contains("Rock"))
        assertTrue(names.contains("Jazz"))
    }

    @Test
    fun `empty input returns empty list`() {
        val result = SeriesDetector.detectSeries(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `albums with no matching pattern return empty`() {
        val albums = setOf("Random Album", "Another Random", "Third One")
        val result = SeriesDetector.detectSeries(albums)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `only two or more same prefix albums form series`() {
        val albums = setOf(
            "Collection Vol. 1", "Collection Vol. 2",
            "Solo Album Vol. 5"
        )
        val result = SeriesDetector.detectSeries(albums)
        assertEquals(1, result.size)
        assertEquals("Collection", result[0].seriesName)
    }

    @Test
    fun `volume info contains original album name`() {
        val albums = setOf("Test Vol. 1", "Test Vol. 2")
        val result = SeriesDetector.detectSeries(albums)
        assertEquals("Test Vol. 1", result[0].volumes[0].albumName)
        assertEquals("Test Vol. 2", result[0].volumes[1].albumName)
    }
}
