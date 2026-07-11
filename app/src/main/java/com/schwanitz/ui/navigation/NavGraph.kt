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
import com.schwanitz.ui.screens.settings.GeneralSettingsScreen
import com.schwanitz.ui.screens.settings.SettingsDashboardScreen
import com.schwanitz.ui.screens.settings.SettingsScreen
import com.schwanitz.ui.screens.songinfo.SongInfoScreen
import com.schwanitz.ui.screens.albumdetail.AlbumDetailScreen
import com.schwanitz.ui.screens.artistdetail.ArtistDetailScreen
import com.schwanitz.ui.screens.seriesdetail.SeriesDetailScreen
import com.schwanitz.ui.screens.yeardetail.YearDetailScreen
import com.schwanitz.ui.screens.genredetail.GenreDetailScreen

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
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { album, albumArtist ->
                    val encodedAlbum = Uri.encode(album)
                    val encodedAlbumArtist = Uri.encode(albumArtist)
                    navController.navigate("album_detail/$encodedAlbum/$encodedAlbumArtist")
                },
                onArtistClick = { artist ->
                    val encodedArtist = Uri.encode(artist)
                    navController.navigate("artist_detail/$encodedArtist")
                },
                onAllArtistsClick = { navController.navigate("all_artists") },
                onAllAlbumsClick = { navController.navigate("all_albums") },
                onAllYearsClick = { navController.navigate("all_years") },
                onAllGenresClick = { navController.navigate("all_genres") },
                onYearClick = { year ->
                    navController.navigate("year_detail/$year")
                },
                onGenreClick = { genre ->
                    val encodedGenre = Uri.encode(genre)
                    navController.navigate("genre_detail/$encodedGenre")
                }
            )
        }

        composable(
            route = "genre_detail/{genreName}",
            arguments = listOf(navArgument("genreName") { type = NavType.StringType })
        ) { backStackEntry ->
            val genreName = backStackEntry.arguments?.getString("genreName") ?: ""
            GenreDetailScreen(
                genre = genreName,
                onNavigateBack = { navController.popBackStack() },
                onArtistClick = { artist ->
                    val encodedArtist = Uri.encode(artist)
                    navController.navigate("artist_detail/$encodedArtist")
                },
                onAlbumClick = { album, albumArtist ->
                    val encodedAlbum = Uri.encode(album)
                    val encodedAlbumArtist = Uri.encode(albumArtist)
                    navController.navigate("album_detail/$encodedAlbum/$encodedAlbumArtist")
                }
            )
        }

        composable(
            route = "year_detail/{year}",
            arguments = listOf(navArgument("year") { type = NavType.IntType })
        ) { backStackEntry ->
            val year = backStackEntry.arguments?.getInt("year") ?: 0
            YearDetailScreen(
                year = year,
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { album, albumArtist ->
                    val encodedAlbum = Uri.encode(album)
                    val encodedAlbumArtist = Uri.encode(albumArtist)
                    navController.navigate("album_detail/$encodedAlbum/$encodedAlbumArtist")
                }
            )
        }

        composable(
            route = "artist_detail/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            ArtistDetailScreen(
                artistName = artistName,
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { album, albumArtist ->
                    val encodedAlbum = Uri.encode(album)
                    val encodedAlbumArtist = Uri.encode(albumArtist)
                    navController.navigate("album_detail/$encodedAlbum/$encodedAlbumArtist")
                }
            )
        }

        composable("all_artists") {
            com.schwanitz.ui.screens.artistlist.ArtistListScreen(
                onNavigateBack = { navController.popBackStack() },
                onArtistClick = { artist ->
                    val encodedArtist = Uri.encode(artist)
                    navController.navigate("artist_detail/$encodedArtist")
                }
            )
        }

        composable("all_albums") {
            com.schwanitz.ui.screens.albumlist.AlbumListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { albumName, albumArtist ->
                    val encoded = Uri.encode(albumName)
                    val encodedArtist = Uri.encode(albumArtist)
                    navController.navigate("album_detail/$encoded/$encodedArtist")
                }
            )
        }

        composable("all_years") {
            com.schwanitz.ui.screens.yearlist.YearListScreen(
                onNavigateBack = { navController.popBackStack() },
                onYearClick = { year ->
                    navController.navigate("year_detail/$year")
                }
            )
        }

        composable("all_genres") {
            com.schwanitz.ui.screens.genrelist.GenreListScreen(
                onNavigateBack = { navController.popBackStack() },
                onGenreClick = { genre ->
                    val encoded = Uri.encode(genre)
                    navController.navigate("genre_detail/$encoded")
                }
            )
        }

        composable(
            route = "album_detail/{albumName}/{albumArtistName}",
            arguments = listOf(
                navArgument("albumName") { type = NavType.StringType },
                navArgument("albumArtistName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
            val albumArtistName = backStackEntry.arguments?.getString("albumArtistName") ?: ""
            AlbumDetailScreen(
                albumName = albumName,
                albumArtistName = albumArtistName,
                onNavigateBack = { navController.popBackStack() },
                onSeriesClick = { seriesName ->
                    val encoded = Uri.encode(seriesName)
                    navController.navigate("series_detail/$encoded")
                }
            )
        }

        composable(
            route = "series_detail/{seriesName}",
            arguments = listOf(navArgument("seriesName") { type = NavType.StringType })
        ) { backStackEntry ->
            val seriesName = backStackEntry.arguments?.getString("seriesName") ?: ""
            SeriesDetailScreen(
                seriesName = seriesName,
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { album, albumArtist ->
                    val encodedAlbum = Uri.encode(album)
                    val encodedAlbumArtist = Uri.encode(albumArtist)
                    navController.navigate("album_detail/$encodedAlbum/$encodedAlbumArtist")
                }
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
            NowPlayingScreen(
                onSongInfoClick = { songId ->
                    val encodedId = Uri.encode(songId)
                    navController.navigate("song_info/$encodedId")
                }
            )
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
                onNavigateAbout = { navController.navigate("about") },
                onNavigateGeneral = { navController.navigate("general_settings") }
            )
        }

        composable("general_settings") {
            GeneralSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
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
