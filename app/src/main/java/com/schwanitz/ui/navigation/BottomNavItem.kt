package com.schwanitz.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector
import com.schwanitz.R

sealed class BottomNavItem(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    data object Songs : BottomNavItem(
        route = "home",
        titleRes = R.string.bottom_songs,
        icon = Icons.Filled.LibraryMusic
    )

    data object Playlists : BottomNavItem(
        route = "playlists",
        titleRes = R.string.bottom_playlists,
        icon = Icons.AutoMirrored.Filled.QueueMusic
    )

    data object NowPlaying : BottomNavItem(
        route = "nowplaying",
        titleRes = R.string.bottom_now_playing,
        icon = Icons.Filled.PlayCircleFilled
    )

    companion object {
        val items = listOf(Songs, Playlists, NowPlaying)
    }
}
