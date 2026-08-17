package com.schwanitz.ui.screens.playlist

import androidx.lifecycle.SavedStateHandle
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistPickerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: PlaylistRepository = mockk(relaxed = true)

    @Test
    fun `successful add reports added and duplicate counts`() = runTest {
        coEvery { repository.getPlaylistSongCount(7L) } returns 3
        coEvery { repository.addSongToPlaylist(7L, "one", 3) } returns true
        coEvery { repository.addSongToPlaylist(7L, "two", 4) } returns false
        val viewModel = viewModelWithSongIds("one,two")
        var outcome: PlaylistAddOutcome? = null

        viewModel.addSongsToPlaylist(7L) { outcome = it }
        advanceUntilIdle()

        assertEquals(PlaylistAddOutcome(addedCount = 1, duplicateCount = 1), outcome)
    }

    @Test
    fun `failed add keeps picker open by not completing`() = runTest {
        coEvery { repository.getPlaylistSongCount(7L) } throws IllegalStateException("db")
        val viewModel = viewModelWithSongIds("one")
        var outcome: PlaylistAddOutcome? = null

        viewModel.addSongsToPlaylist(7L) { outcome = it }
        advanceUntilIdle()

        assertNull(outcome)
    }

    private fun viewModelWithSongIds(songIds: String) = PlaylistPickerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("songIds" to songIds)),
        playlistRepository = repository
    )
}
