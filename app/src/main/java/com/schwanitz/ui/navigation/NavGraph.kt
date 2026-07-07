package com.schwanitz.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.schwanitz.ui.screens.home.HomeScreen
import com.schwanitz.ui.screens.nowplaying.NowPlayingScreen
import com.schwanitz.ui.screens.playlist.PlaylistDetailScreen
import com.schwanitz.ui.screens.playlist.PlaylistListScreen
import com.schwanitz.ui.screens.playlist.SelectSongsScreen
import com.schwanitz.ui.screens.settings.AboutScreen
import com.schwanitz.ui.screens.settings.AddSourceScreen
import com.schwanitz.ui.screens.settings.SettingsDashboardScreen
import com.schwanitz.ui.screens.settings.SettingsScreen
import com.schwanitz.ui.screens.songinfo.SongInfoScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Songs.route
    ) {
        composable(BottomNavItem.Songs.route) {
            HomeScreen(
                onSettingsClick = { navController.navigate("settings") },
                onSongInfoClick = { songId -> 
                    val encodedId = Uri.encode(songId)
                    navController.navigate("song_info/$encodedId") 
                }
            )
        }

        composable("song_info/{songId}") { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            SongInfoScreen(
                songId = songId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(BottomNavItem.Playlists.route) {
            PlaylistListScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate("playlist_detail/$playlistId")
                }
            )
        }

        composable(BottomNavItem.NowPlaying.route) {
            NowPlayingScreen()
        }

        composable("playlist_detail/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                onNavigateBack = { navController.popBackStack() },
                onAddSongsClick = { navController.navigate("select_songs/$playlistId") }
            )
        }

        composable("select_songs/{playlistId}") {
            SelectSongsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateSources = { navController.navigate("source_settings") },
                onNavigateAbout = { navController.navigate("about") }
            )
        }

        composable("source_settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddSource = { navController.navigate("add_source") },
                onEditSource = { sourceId -> navController.navigate("add_source?sourceId=$sourceId") }
            )
        }

        composable("about") {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "add_source?sourceId={sourceId}",
            arguments = listOf(navArgument("sourceId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getString("sourceId")
            AddSourceScreen(
                sourceId = sourceId,
                onNavigateBack = { navController.popBackStack() },
                onSourceAdded = {
                    navController.popBackStack("source_settings", inclusive = false)
                }
            )
        }
    }
}
