package com.schwanitz.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Songs : BottomNavItem(
        route = "home",
        title = "Songs",
        icon = Icons.Filled.LibraryMusic
    )

    data object Playlists : BottomNavItem(
        route = "playlists",
        title = "Playlists",
        icon = Icons.Filled.QueueMusic
    )

    data object NowPlaying : BottomNavItem(
        route = "nowplaying",
        title = "Now Playing",
        icon = Icons.Filled.PlayCircleFilled
    )

    companion object {
        val items = listOf(Songs, Playlists, NowPlaying)
    }
}
