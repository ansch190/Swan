package com.schwanitz.data.repository

import com.schwanitz.domain.repository.*
import com.schwanitz.domain.source.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLifecycleManagerImplTest {
    private val songRepository = mockk<SongRepository>(relaxed = true)
    private val lyricsRepository = mockk<SongLyricsRepository>(relaxed = true)
    private val sourceManager = mockk<SourceManager>()
    private val orchestrator = mockk<ScanOrchestrator>(relaxed = true)
    private val config = SourceConfig("source", "Test", SourceType.LOCAL)

    @Test
    fun `fatal scan aborts staging and never deletes live songs`() = runTest {
        val source = fakeSource { _, _ -> error("offline") }
        coEvery { sourceManager.getSourceById("source") } returns config
        coEvery { orchestrator.beginScan("source") } returns "session"
        val manager = manager(source)

        val result = manager.refreshSource("source")

        assertTrue(result is SourceRefreshResult.Failure)
        coVerify(exactly = 1) { orchestrator.abortScan("session") }
        coVerify(exactly = 0) { songRepository.deleteBySource(any()) }
        coVerify(exactly = 0) { lyricsRepository.deleteBySource(any()) }
    }

    @Test
    fun `successful scan stages events and commits once`() = runTest {
        val summary = ScanSummary(total = 1, succeeded = 0, failedIds = setOf("song"))
        val source = fakeSource { _, emit ->
            emit(ScanEvent.Discovered(listOf("song")))
            summary
        }
        coEvery { sourceManager.getSourceById("source") } returns config
        coEvery { orchestrator.beginScan("source") } returns "session"
        val manager = manager(source)

        val result = manager.refreshSource("source")

        assertEquals(SourceRefreshResult.Success(1, 0, 1), result)
        coVerify { orchestrator.stageEvent("session", ScanEvent.Discovered(listOf("song"))) }
        coVerify { orchestrator.commitScan("session", "source", true, summary) }
        coVerify(exactly = 0) { orchestrator.abortScan(any()) }
    }

    @Test
    fun `successfully enumerated empty source commits empty summary`() = runTest {
        val summary = ScanSummary(0, 0)
        val source = fakeSource { _, _ -> summary }
        coEvery { sourceManager.getSourceById("source") } returns config
        coEvery { orchestrator.beginScan("source") } returns "session"

        assertEquals(SourceRefreshResult.Success(0, 0, 0), manager(source).refreshSource("source"))
        coVerify { orchestrator.commitScan("session", "source", true, summary) }
    }

    @Test
    fun `cancelled scan aborts staging and propagates cancellation`() = runTest {
        val source = fakeSource { _, _ -> throw CancellationException("cancel") }
        coEvery { sourceManager.getSourceById("source") } returns config
        coEvery { orchestrator.beginScan("source") } returns "session"

        try {
            manager(source).refreshSource("source")
            throw AssertionError("CancellationException expected")
        } catch (_: CancellationException) {
            coVerify { orchestrator.abortScan("session") }
        }
    }

    @Test
    fun `duplicate refresh requests share one scan`() = runTest {
        var invocations = 0
        val source = fakeSource { _, _ ->
            invocations++
            delay(10)
            ScanSummary(0, 0)
        }
        coEvery { sourceManager.getSourceById("source") } returns config
        coEvery { orchestrator.beginScan("source") } returns "session"
        val manager = manager(source)

        val first = async { manager.refreshSource("source") }
        val second = async { manager.refreshSource("source") }

        assertEquals(first.await(), second.await())
        assertEquals(1, invocations)
        coVerify(exactly = 1) { orchestrator.commitScan("session", "source", true, any()) }
    }

    private fun manager(source: MusicSource) = SourceLifecycleManagerImpl(
        songRepository,
        lyricsRepository,
        sourceManager,
        MusicSourceRegistry(setOf(source)),
        orchestrator,
        LibraryOperationCoordinator(),
    )

    private fun fakeSource(
        block: suspend (SourceConfig, suspend (ScanEvent) -> Unit) -> ScanSummary
    ) = object : MusicSource {
        override val type = SourceType.LOCAL
        override suspend fun loadSongs(
            config: SourceConfig,
            onProgress: (Int, Int) -> Unit,
            onEvent: suspend (ScanEvent) -> Unit
        ): ScanSummary = block(config, onEvent)
    }
}
