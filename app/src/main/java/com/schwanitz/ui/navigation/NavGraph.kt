package com.schwanitz.ui.navigation

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
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSongInfoClick = { songId ->
                    navController.navigate(Routes.songInfo(songId))
                }
            )
        }

        composable(
            route = "song_info/{songId}",
            arguments = listOf(navArgument("songId") { type = NavType.StringType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
            SongInfoScreen(
                songId = songId,
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { album, albumArtist ->
                    navController.navigate(Routes.albumDetail(album, albumArtist))
                },
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                },
                onAllArtistsClick = { navController.navigate(Routes.ALL_ARTISTS) },
                onAllAlbumsClick = { navController.navigate(Routes.ALL_ALBUMS) },
                onAllYearsClick = { navController.navigate(Routes.ALL_YEARS) },
                onAllGenresClick = { navController.navigate(Routes.ALL_GENRES) },
                onAllSeriesClick = { navController.navigate(Routes.ALL_SERIES) },
                onYearClick = { year ->
                    navController.navigate(Routes.yearDetail(year))
                },
                onGenreClick = { genre ->
                    navController.navigate(Routes.genreDetail(genre))
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(Routes.seriesDetail(seriesName))
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
                    navController.navigate(Routes.artistDetail(artist))
                },
                onAlbumClick = { album, albumArtist ->
                    navController.navigate(Routes.albumDetail(album, albumArtist))
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
                    navController.navigate(Routes.albumDetail(album, albumArtist))
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
                    navController.navigate(Routes.albumDetail(album, albumArtist))
                }
            )
        }

        composable(Routes.ALL_ARTISTS) {
            com.schwanitz.ui.screens.artistlist.ArtistListScreen(
                onNavigateBack = { navController.popBackStack() },
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                }
            )
        }

        composable(Routes.ALL_ALBUMS) {
            com.schwanitz.ui.screens.albumlist.AlbumListScreen(
                onNavigateBack = { navController.popBackStack() },
                onAlbumClick = { albumName, albumArtist ->
                    navController.navigate(Routes.albumDetail(albumName, albumArtist))
                }
            )
        }

        composable(Routes.ALL_YEARS) {
            com.schwanitz.ui.screens.yearlist.YearListScreen(
                onNavigateBack = { navController.popBackStack() },
                onYearClick = { year ->
                    navController.navigate(Routes.yearDetail(year))
                }
            )
        }

        composable(Routes.ALL_GENRES) {
            com.schwanitz.ui.screens.genrelist.GenreListScreen(
                onNavigateBack = { navController.popBackStack() },
                onGenreClick = { genre ->
                    navController.navigate(Routes.genreDetail(genre))
                }
            )
        }

        composable(Routes.ALL_SERIES) {
            com.schwanitz.ui.screens.serieslist.SeriesListScreen(
                onNavigateBack = { navController.popBackStack() },
                onSeriesClick = { seriesName ->
                    navController.navigate(Routes.seriesDetail(seriesName))
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
                    navController.navigate(Routes.seriesDetail(seriesName))
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
                    navController.navigate(Routes.albumDetail(album, albumArtist))
                }
            )
        }

        composable(BottomNavItem.Playlists.route) {
            PlaylistListScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
                }
            )
        }

        composable(BottomNavItem.NowPlaying.route) {
            NowPlayingScreen(
                onSongInfoClick = { songId ->
                    navController.navigate(Routes.songInfo(songId))
                }
            )
        }

        composable(
            route = "playlist_detail/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                onNavigateBack = { navController.popBackStack() },
                onAddSongsClick = { navController.navigate(Routes.selectSongs(playlistId)) }
            )
        }

        composable(
            route = "select_songs/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) {
            SelectSongsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateSources = { navController.navigate(Routes.SOURCE_SETTINGS) },
                onNavigateAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateGeneral = { navController.navigate(Routes.GENERAL_SETTINGS) }
            )
        }

        composable(Routes.GENERAL_SETTINGS) {
            GeneralSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SOURCE_SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddSource = { navController.navigate(Routes.ADD_SOURCE) },
                onEditSource = { sourceId -> navController.navigate(Routes.addSource(sourceId)) }
            )
        }

        composable(Routes.ABOUT) {
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
                    navController.popBackStack(Routes.SOURCE_SETTINGS, inclusive = false)
                }
            )
        }
    }
}
