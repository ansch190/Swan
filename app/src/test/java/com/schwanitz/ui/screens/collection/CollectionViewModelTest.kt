package com.schwanitz.ui.screens.collection

import app.cash.turbine.test
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.repository.SeriesRepository
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.SongCollectionCounts
import com.schwanitz.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class CollectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val counts = MutableStateFlow(SongCollectionCounts(0, 0, 0, 0))
    private val seriesCount = MutableStateFlow(0)

    private fun createViewModel(): CollectionViewModel {
        val songRepository = mockk<SongRepository>()
        val seriesRepository = mockk<SeriesRepository>()
        every { songRepository.observeCollectionCounts() } returns counts
        every { seriesRepository.observeSeriesCount() } returns seriesCount
        return CollectionViewModel(songRepository, seriesRepository)
    }

    @Test
    fun `empty library exposes loaded zero counts`() = runTest {
        createViewModel().uiState.test {
            val state = awaitLoadedState()
            assertFalse(state.isLoading)
            assertEquals(0, state.albumCount)
            assertEquals(0, state.albumArtistCount)
            assertEquals(0, state.genreCount)
            assertEquals(0, state.yearCount)
            assertEquals(0, state.seriesCount)
        }
    }

    @Test
    fun `all collection counts are derived from repository flows`() = runTest {
        counts.value = SongCollectionCounts(2, 2, 3, 2)
        seriesCount.value = 1

        createViewModel().uiState.test {
            val state = awaitLoadedState()
            assertEquals(2, state.albumCount)
            assertEquals(2, state.albumArtistCount)
            assertEquals(3, state.genreCount)
            assertEquals(2, state.yearCount)
            assertEquals(1, state.seriesCount)
        }
    }

    @Test
    fun `albums without album artist form one additional entry`() = runTest {
        counts.value = SongCollectionCounts(0, 2, 0, 0)

        createViewModel().uiState.test {
            assertEquals(2, awaitLoadedState().albumArtistCount)
        }
    }

    @Test
    fun `counts update reactively`() = runTest {
        createViewModel().uiState.test {
            awaitLoadedState()
            counts.value = SongCollectionCounts(1, 0, 0, 0)
            assertEquals(1, awaitItem().albumCount)
            seriesCount.value = 2
            assertEquals(2, awaitItem().seriesCount)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CollectionUiState>.awaitLoadedState(): CollectionUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }
}
