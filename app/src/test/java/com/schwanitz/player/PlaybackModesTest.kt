package com.schwanitz.player

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PlaybackModesTest {
    @Test
    fun `shuffle enables repeat all through shared policy`() {
        val player = mockk<Player>(relaxed = true)
        every { player.repeatMode } returns Player.REPEAT_MODE_OFF
        every { player.shuffleModeEnabled } returns false andThen true

        PlaybackModes.toggleShuffle(player)

        verify { player.shuffleModeEnabled = true }
        verify { player.repeatMode = Player.REPEAT_MODE_ALL }
    }

    @Test
    fun `repeat one blocks shuffle`() {
        val player = mockk<Player>(relaxed = true)
        every { player.repeatMode } returns Player.REPEAT_MODE_ONE

        PlaybackModes.toggleShuffle(player)

        verify(exactly = 0) { player.shuffleModeEnabled = any() }
    }

    @Test
    fun `repeat cycle leaves shuffle after repeat all`() {
        val player = mockk<Player>(relaxed = true)
        every { player.shuffleModeEnabled } returns true
        every { player.repeatMode } returns Player.REPEAT_MODE_ALL

        PlaybackModes.cycleRepeat(player)

        verify { player.shuffleModeEnabled = false }
        verify { player.repeatMode = Player.REPEAT_MODE_OFF }
    }
}
