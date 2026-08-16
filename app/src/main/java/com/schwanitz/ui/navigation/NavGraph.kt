package com.schwanitz.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.schwanitz.ui.screens.collection.CollectionScreen
import com.schwanitz.ui.screens.home.HomeScreen
import com.schwanitz.ui.screens.nowplaying.NowPlayingScreen
import com.schwanitz.ui.screens.playlist.PlaylistDetailScreen
import com.schwanitz.ui.screens.playlist.PlaylistListScreen
import com.schwanitz.ui.screens.playlist.PlaylistPickerScreen
import com.schwanitz.ui.screens.playlist.SelectSongsScreen
import com.schwanitz.ui.screens.settings.AboutScreen
import com.schwanitz.ui.screens.settings.AddSourceScreen
import com.schwanitz.ui.screens.settings.BackupScreen
import com.schwanitz.ui.screens.settings.ArtistDataSourceScreen
import com.schwanitz.ui.screens.settings.GeneralSettingsScreen
import com.schwanitz.ui.screens.settings.SettingsDashboardScreen
import com.schwanitz.ui.screens.settings.SettingsScreen
import com.schwanitz.ui.screens.songinfo.SongInfoScreen
import com.schwanitz.ui.screens.albumdetail.AlbumDetailScreen
import com.schwanitz.ui.screens.artistdetail.ArtistBiographyScreen
import com.schwanitz.ui.screens.artistdetail.ArtistDetailScreen
import com.schwanitz.ui.screens.artistdetail.ArtistDetailViewModel
import com.schwanitz.ui.screens.seriesdetail.SeriesDetailScreen
import com.schwanitz.ui.screens.yeardetail.YearDetailScreen
import com.schwanitz.ui.screens.genredetail.GenreDetailScreen
import com.schwanitz.ui.screens.decadedetail.DecadeDetailScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Songs.route
    ) {
        navigation(
            route = BottomNavItem.Songs.route,
            startDestination = BottomNavItem.Songs.startDestination
        ) {
        composable(BottomNavItem.Songs.startDestination) {
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
                onAlbumClick = { album, albumArtist, year ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Collection,
                        Routes.albumDetail(album, albumArtist, year)
                    )
                },
                onAllAlbumArtistsClick = {
                    navController.navigateToTopLevelDestination(BottomNavItem.Collection, Routes.ALL_ARTISTS)
                },
                onAlbumArtistClick = { artist ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Collection,
                        Routes.artistDetail(artist)
                    )
                },
                onAllAlbumsClick = {
                    navController.navigateToTopLevelDestination(BottomNavItem.Collection, Routes.ALL_ALBUMS)
                },
                onAllYearsClick = {
                    navController.navigateToTopLevelDestination(BottomNavItem.Collection, Routes.ALL_YEARS)
                },
                onAllGenresClick = {
                    navController.navigateToTopLevelDestination(BottomNavItem.Collection, Routes.ALL_GENRES)
                },
                onAllSeriesClick = {
                    navController.navigateToTopLevelDestination(BottomNavItem.Collection, Routes.ALL_SERIES)
                },
                onYearClick = { year ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Collection,
                        Routes.yearDetail(year)
                    )
                },
                onGenreClick = { genre ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Collection,
                        Routes.genreDetail(genre)
                    )
                },
                onSeriesClick = { seriesName ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Collection,
                        Routes.seriesDetail(seriesName)
                    )
                }
            )
        }

        settingsDestinations(navController)
        }

        navigation(
            route = BottomNavItem.Collection.route,
            startDestination = BottomNavItem.Collection.startDestination
        ) {
        composable(BottomNavItem.Collection.startDestination) {
            CollectionScreen(
                onAlbumsClick = { navController.navigate(Routes.ALL_ALBUMS) },
                onAlbumArtistsClick = { navController.navigate(Routes.ALL_ARTISTS) },
                onGenresClick = { navController.navigate(Routes.ALL_GENRES) },
                onYearsClick = { navController.navigate(Routes.ALL_YEARS) },
                onSeriesClick = { navController.navigate(Routes.ALL_SERIES) }
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
                onAlbumClick = { album, albumArtist, year ->
                    navController.navigate(Routes.albumDetail(album, albumArtist, year))
                },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
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
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                },
                onAlbumClick = { album, albumArtist, albumYear ->
                    navController.navigate(Routes.albumDetail(album, albumArtist, albumYear))
                },
                onAllYearsClick = { navController.navigate(Routes.ALL_YEARS) },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
                }
            )
        }

        composable(
            route = "decade_detail/{decade}",
            arguments = listOf(navArgument("decade") { type = NavType.IntType })
        ) { backStackEntry ->
            val decade = backStackEntry.arguments?.getInt("decade") ?: 0
            DecadeDetailScreen(
                decade = decade,
                onNavigateBack = { navController.popBackStack() },
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                },
                onAlbumClick = { album, albumArtist, year ->
                    navController.navigate(Routes.albumDetail(album, albumArtist, year))
                },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
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
                onAlbumClick = { album, albumArtist, year ->
                    navController.navigate(Routes.albumDetail(album, albumArtist, year))
                },
                onBioClick = { navController.navigate(Routes.artistBiography(artistName)) },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
                }
            )
        }

        composable(
            route = "artist_biography/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
            val artistEntry = remember(backStackEntry) {
                navController.getBackStackEntry("artist_detail/$artistName")
            }
            val artistVm: ArtistDetailViewModel = hiltViewModel(artistEntry)
            ArtistBiographyScreen(
                artistName = artistName,
                onNavigateBack = { navController.popBackStack() },
                viewModel = artistVm
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
                onAlbumClick = { albumName, albumArtist, year ->
                    navController.navigate(Routes.albumDetail(albumName, albumArtist, year))
                }
            )
        }

        composable(Routes.ALL_YEARS) {
            com.schwanitz.ui.screens.yearlist.YearListScreen(
                onNavigateBack = { navController.popBackStack() },
                onYearClick = { year ->
                    navController.navigate(Routes.yearDetail(year))
                },
                onDecadeClick = { decade ->
                    navController.navigate(Routes.decadeDetail(decade))
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
            route = "album_detail/{albumName}/{albumArtistName}/{albumYear}",
            arguments = listOf(
                navArgument("albumName") { type = NavType.StringType },
                navArgument("albumArtistName") { type = NavType.StringType },
                navArgument("albumYear") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
            val albumArtistName = backStackEntry.arguments?.getString("albumArtistName") ?: ""
            val albumYear = backStackEntry.arguments?.getInt("albumYear") ?: 0
            AlbumDetailScreen(
                albumName = albumName,
                albumArtistName = albumArtistName,
                albumYear = albumYear,
                onNavigateBack = { navController.popBackStack() },
                onArtistClick = { artist ->
                    navController.navigate(Routes.artistDetail(artist))
                },
                onSeriesClick = { seriesName ->
                    navController.navigate(Routes.seriesDetail(seriesName))
                },
                onYearClick = { year ->
                    navController.navigate(Routes.yearDetail(year))
                },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
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
                onAlbumClick = { album, albumArtist, year ->
                    navController.navigate(Routes.albumDetail(album, albumArtist, year))
                },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
                },
                onAllSeriesClick = { navController.navigate(Routes.ALL_SERIES) }
            )
        }
        }

        navigation(
            route = BottomNavItem.Playlists.route,
            startDestination = BottomNavItem.Playlists.startDestination
        ) {
        composable(BottomNavItem.Playlists.startDestination) {
            PlaylistListScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(Routes.playlistDetail(playlistId))
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
                onAddSongsClick = { navController.navigate(Routes.selectSongs(playlistId)) },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
                }
            )
        }

        composable(
            route = "select_songs/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            val playlistDetailBackStackEntry = remember(backStackEntry) {
                navController.getBackStackEntry("playlist_detail/$playlistId")
            }
            val playlistDetailViewModel: com.schwanitz.ui.screens.playlist.PlaylistDetailViewModel = hiltViewModel(playlistDetailBackStackEntry)
            SelectSongsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSongsSelected = { songs ->
                    playlistDetailViewModel.queueSongAdditions(songs)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "playlist_picker/{songIds}",
            arguments = listOf(navArgument("songIds") { type = NavType.StringType })
        ) {
            PlaylistPickerScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlaylistSelected = { playlistId ->
                    navController.popBackStack()
                    navController.navigate(Routes.playlistDetail(playlistId))
                }
            )
        }
        }

        navigation(
            route = BottomNavItem.NowPlaying.route,
            startDestination = BottomNavItem.NowPlaying.startDestination
        ) {
        composable(BottomNavItem.NowPlaying.startDestination) {
            NowPlayingScreen(
                onSongInfoClick = { songId ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Songs,
                        Routes.songInfo(songId)
                    )
                },
                onAddToPlaylist = { songIds ->
                    navController.navigateToTopLevelDestination(
                        BottomNavItem.Playlists,
                        Routes.playlistPicker(songIds)
                    )
                }
            )
        }
        }

    }
}

private fun NavGraphBuilder.settingsDestinations(navController: NavHostController) {
        composable(Routes.SETTINGS) {
            SettingsDashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateSources = { navController.navigate(Routes.SOURCE_SETTINGS) },
                onNavigateArtistData = { navController.navigate(Routes.ARTIST_DATA_SOURCE) },
                onNavigateBackup = { navController.navigate(Routes.BACKUP) },
                onNavigateAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateGeneral = { navController.navigate(Routes.GENERAL_SETTINGS) }
            )
        }

        composable(Routes.BACKUP) {
            BackupScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSources = {
                    navController.navigate(Routes.SOURCE_SETTINGS) {
                        popUpTo(Routes.SETTINGS)
                    }
                }
            )
        }

        composable(Routes.GENERAL_SETTINGS) {
            GeneralSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ARTIST_DATA_SOURCE) {
            ArtistDataSourceScreen(
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
