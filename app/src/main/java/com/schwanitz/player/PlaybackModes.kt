package com.schwanitz.player

import androidx.media3.common.Player

object PlaybackModes {
    fun toggleShuffle(player: Player) {
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        player.repeatMode = if (player.shuffleModeEnabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun cycleRepeat(player: Player) {
        player.repeatMode = when {
            player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ALL -> {
                player.shuffleModeEnabled = false
                Player.REPEAT_MODE_OFF
            }
            player.repeatMode == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            player.repeatMode == Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
