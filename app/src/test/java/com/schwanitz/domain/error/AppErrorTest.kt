package com.schwanitz.domain.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppErrorTest {

    @Test
    fun `from UnknownHostException returns Network error`() {
        val error = AppError.from(UnknownHostException("Host not found"))
        assertTrue(error is AppError.Network)
        assertEquals("Host not found", error.toUserMessage())
    }

    @Test
    fun `from SocketTimeoutException returns Network error`() {
        val error = AppError.from(SocketTimeoutException("Timed out"))
        assertTrue(error is AppError.Network)
        assertEquals("Timed out", error.toUserMessage())
    }

    @Test
    fun `from IOException returns Network error`() {
        val error = AppError.from(IOException("Connection refused"))
        assertTrue(error is AppError.Network)
        assertEquals("Connection refused", error.toUserMessage())
    }

    @Test
    fun `from OutOfMemoryError returns Io error`() {
        val error = AppError.from(OutOfMemoryError())
        assertTrue(error is AppError.Io)
        assertEquals("Out of memory", error.toUserMessage())
    }

    @Test
    fun `from generic exception returns Unknown error`() {
        val error = AppError.from(IllegalStateException("Something broke"))
        assertTrue(error is AppError.Unknown)
        assertEquals("Something broke", error.toUserMessage())
    }

    @Test
    fun `from uses fallback when message is null`() {
        val error = AppError.from(RuntimeException(), "Custom fallback")
        assertTrue(error is AppError.Unknown)
        assertEquals("Custom fallback", error.toUserMessage())
    }

    @Test
    fun `toUserMessage returns default for blank Network message`() {
        val error = AppError.Network(message = "")
        assertEquals("Network error", error.toUserMessage())
    }

    @Test
    fun `toUserMessage returns default for blank Database message`() {
        val error = AppError.Database(message = "")
        assertEquals("Database error", error.toUserMessage())
    }

    @Test
    fun `toUserMessage returns default for blank Playback message`() {
        val error = AppError.Playback(message = "")
        assertEquals("Playback error", error.toUserMessage())
    }

    @Test
    fun `toUserMessage returns default for blank Io message`() {
        val error = AppError.Io(message = "")
        assertEquals("Storage error", error.toUserMessage())
    }

    @Test
    fun `toUserMessage returns default for blank Unknown message`() {
        val error = AppError.Unknown(message = "")
        assertEquals("An error occurred", error.toUserMessage())
    }

    @Test
    fun `toUserMessage includes source name for blank Source message`() {
        val error = AppError.Source(sourceName = "My NAS", message = "")
        assertEquals("Source error: My NAS", error.toUserMessage())
    }

    @Test
    fun `toUserMessage returns custom message when present`() {
        val error = AppError.Network(message = "Custom network error")
        assertEquals("Custom network error", error.toUserMessage())
    }

    @Test
    fun `factory network creates Network error`() {
        val cause = IOException("test")
        val error = AppError.network(cause = cause, message = "msg")
        assertTrue(error is AppError.Network)
        assertEquals("msg", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `factory database creates Database error`() {
        val error = AppError.database(message = "db fail")
        assertTrue(error is AppError.Database)
        assertEquals("db fail", error.message)
    }

    @Test
    fun `factory source creates Source error`() {
        val error = AppError.source("WebDAV", "connection lost")
        assertTrue(error is AppError.Source)
        assertEquals("WebDAV", error.sourceName)
        assertEquals("connection lost", error.message)
    }

    @Test
    fun `factory playback creates Playback error`() {
        val error = AppError.playback(message = "codec error")
        assertTrue(error is AppError.Playback)
        assertEquals("codec error", error.message)
    }

    @Test
    fun `factory io creates Io error`() {
        val error = AppError.io(message = "disk full")
        assertTrue(error is AppError.Io)
        assertEquals("disk full", error.message)
    }

    @Test
    fun `factory unknown creates Unknown error`() {
        val error = AppError.unknown(message = "oops")
        assertTrue(error is AppError.Unknown)
        assertEquals("oops", error.message)
    }

    @Test
    fun `from preserves cause in Network error`() {
        val cause = UnknownHostException("host")
        val error = AppError.from(cause)
        assertTrue(error is AppError.Network)
        assertEquals(cause, (error as AppError.Network).cause)
    }
}
