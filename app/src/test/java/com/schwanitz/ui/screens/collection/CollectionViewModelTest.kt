package com.schwanitz.ui.screens.collection

import app.cash.turbine.test
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.repository.SeriesRepository
import com.schwanitz.domain.repository.SongRepository
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

    private val albums = MutableStateFlow<List<Album>>(emptyList())
    private val albumArtists = MutableStateFlow<List<String>>(emptyList())
    private val hasAlbumsWithoutArtist = MutableStateFlow(false)
    private val genres = MutableStateFlow<List<String>>(emptyList())
    private val years = MutableStateFlow<List<Int>>(emptyList())
    private val series = MutableStateFlow<List<AlbumSeries>>(emptyList())

    private fun createViewModel(): CollectionViewModel {
        val songRepository = mockk<SongRepository>()
        val seriesRepository = mockk<SeriesRepository>()
        every { songRepository.getAllAlbums() } returns albums
        every { songRepository.getAllAlbumArtistNames() } returns albumArtists
        every { songRepository.hasAlbumsWithNoAlbumArtist() } returns hasAlbumsWithoutArtist
        every { songRepository.getAllGenres() } returns genres
        every { songRepository.getAllYears() } returns years
        every { seriesRepository.getAlbumSeries() } returns series
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
        albums.value = listOf(
            Album(id = 1, name = "First"),
            Album(id = 2, name = "Second")
        )
        albumArtists.value = listOf("Artist A", "Artist B")
        genres.value = listOf("Jazz", "Rock", "Soul")
        years.value = listOf(1999, 2000)
        series.value = listOf(AlbumSeries(1, "Series A"))

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
        albumArtists.value = listOf("Named Artist")
        hasAlbumsWithoutArtist.value = true

        createViewModel().uiState.test {
            assertEquals(2, awaitLoadedState().albumArtistCount)
        }
    }

    @Test
    fun `counts update reactively`() = runTest {
        createViewModel().uiState.test {
            awaitLoadedState()
            albums.value = listOf(Album(id = 1, name = "New Album"))
            assertEquals(1, awaitItem().albumCount)
            series.value = listOf(
                AlbumSeries(1, "One"),
                AlbumSeries(2, "Two")
            )
            assertEquals(2, awaitItem().seriesCount)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<CollectionUiState>.awaitLoadedState(): CollectionUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }
}
