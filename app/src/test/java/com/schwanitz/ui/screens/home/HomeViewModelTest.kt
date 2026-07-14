package com.schwanitz.ui.screens.home

import app.cash.turbine.test
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var songRepository: SongRepository
    private lateinit var playerManager: MusicPlayerManager
    private lateinit var viewModel: HomeViewModel

    private val testSongs = listOf(
        Song(id = "1", title = "Alpha Song", artistName = "Alpha Artist", albumName = "Alpha Album", durationMs = 200_000, sourceId = "local"),
        Song(id = "2", title = "Beta Song", artistName = "Beta Artist", albumName = "Beta Album", durationMs = 180_000, sourceId = "local"),
        Song(id = "3", title = "Gamma Song", artistName = "Alpha Artist", albumName = "Gamma Album", durationMs = 240_000, sourceId = "local", isFavorite = true),
        Song(id = "4", title = "Delta Song", artistName = "Delta Artist", albumName = "Alpha Album", durationMs = 300_000, sourceId = "local")
    )

    @Before
    fun setUp() {
        songRepository = mockk(relaxed = true)
        playerManager = mockk(relaxed = true)
        every { songRepository.getAllSongs() } returns flowOf(testSongs)
        every { songRepository.getFavoriteSongs() } returns flowOf(testSongs.filter { it.isFavorite })
        viewModel = HomeViewModel(songRepository, playerManager)
    }

    @Test
    fun `initial state loads all songs`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(4, state.songs.size)
            assertEquals("", state.searchQuery)
            assertFalse(state.showFavoritesOnly)
        }
    }

    @Test
    fun `search filters by title`() = runTest {
        viewModel.uiState.test {
            skipItems(1) // skip initial
            viewModel.onSearchQueryChange("Beta Song")
            val state = awaitItem()
            assertEquals(1, state.songs.size)
            assertEquals("Beta Song", state.songs[0].title)
        }
    }

    @Test
    fun `search filters by artist name`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onSearchQueryChange("Beta Artist")
            val state = awaitItem()
            assertEquals(1, state.songs.size)
            assertEquals("Beta Song", state.songs[0].title)
        }
    }

    @Test
    fun `search filters by album name`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onSearchQueryChange("Gamma Album")
            val state = awaitItem()
            assertEquals(1, state.songs.size)
            assertEquals("Gamma Song", state.songs[0].title)
        }
    }

    @Test
    fun `search is case insensitive`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onSearchQueryChange("beta")
            val state = awaitItem()
            assertEquals(1, state.songs.size)
        }
    }

    @Test
    fun `search matches multiple fields`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onSearchQueryChange("Alpha")
            val state = awaitItem()
            // "Alpha Song" (title), "Alpha Artist" (artist), and two songs with "Alpha Album"
            // Song 1: title match + album match, Song 3: artist match, Song 4: album match
            assertEquals(3, state.songs.size)
        }
    }

    @Test
    fun `empty search shows all songs`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onSearchQueryChange("Alpha")
            awaitItem() // filtered
            viewModel.onSearchQueryChange("")
            val state = awaitItem()
            assertEquals(4, state.songs.size)
        }
    }

    @Test
    fun `favorites filter shows only favorites`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.toggleFavoritesFilter()
            val state = awaitItem()
            assertTrue(state.showFavoritesOnly)
            assertEquals(1, state.songs.size)
            assertTrue(state.songs[0].isFavorite)
        }
    }

    @Test
    fun `toggle favorites twice shows all songs`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.toggleFavoritesFilter()
            awaitItem() // favorites only
            viewModel.toggleFavoritesFilter()
            val state = awaitItem()
            assertFalse(state.showFavoritesOnly)
            assertEquals(4, state.songs.size)
        }
    }

    @Test
    fun `search query is stored in state`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            viewModel.onSearchQueryChange("test")
            val state = awaitItem()
            assertEquals("test", state.searchQuery)
        }
    }

    @Test
    fun `playSong delegates to playerManager`() = runTest {
        val song = testSongs[0]
        viewModel.playSong(song)
        verify { playerManager.play(song, listOf(song)) }
    }

    @Test
    fun `toggleFavorite delegates to repository`() = runTest {
        viewModel.toggleFavorite(testSongs[0])
        coVerify { songRepository.toggleFavorite("1") }
    }
}
