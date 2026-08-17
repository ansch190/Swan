package com.schwanitz.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.schwanitz.R
import com.schwanitz.ui.screens.albumdetail.AlbumDetailScreen
import com.schwanitz.ui.screens.albumlist.AlbumListScreen
import com.schwanitz.ui.screens.artistdetail.ArtistBiographyScreen
import com.schwanitz.ui.screens.artistdetail.ArtistDetailScreen
import com.schwanitz.ui.screens.artistdetail.ArtistDetailViewModel
import com.schwanitz.ui.screens.artistlist.ArtistListScreen
import com.schwanitz.ui.screens.collection.CollectionScreen
import com.schwanitz.ui.screens.decadedetail.DecadeDetailScreen
import com.schwanitz.ui.screens.genredetail.GenreDetailScreen
import com.schwanitz.ui.screens.genrelist.GenreListScreen
import com.schwanitz.ui.screens.home.HomeScreen
import com.schwanitz.ui.screens.nowplaying.NowPlayingScreen
import com.schwanitz.ui.screens.playlist.PlaylistAddOutcome
import com.schwanitz.ui.screens.playlist.PlaylistDetailScreen
import com.schwanitz.ui.screens.playlist.PlaylistDetailViewModel
import com.schwanitz.ui.screens.playlist.PlaylistListScreen
import com.schwanitz.ui.screens.playlist.PlaylistPickerScreen
import com.schwanitz.ui.screens.playlist.SelectSongsScreen
import com.schwanitz.ui.screens.seriesdetail.SeriesDetailScreen
import com.schwanitz.ui.screens.serieslist.SeriesListScreen
import com.schwanitz.ui.screens.settings.AboutScreen
import com.schwanitz.ui.screens.settings.AddSourceScreen
import com.schwanitz.ui.screens.settings.ArtistDataSourceScreen
import com.schwanitz.ui.screens.settings.BackupScreen
import com.schwanitz.ui.screens.settings.GeneralSettingsScreen
import com.schwanitz.ui.screens.settings.SettingsDashboardScreen
import com.schwanitz.ui.screens.settings.SettingsScreen
import com.schwanitz.ui.screens.songinfo.SongInfoScreen
import com.schwanitz.ui.screens.yeardetail.YearDetailScreen
import com.schwanitz.ui.screens.yearlist.YearListScreen
import kotlinx.coroutines.launch

@Composable
fun NavGraph(
    navController: NavHostController,
    playerUiResetToken: Long = 0L
) {
    val songsRoutes = Routes.scoped(BottomNavItem.Songs)
    val collectionRoutes = Routes.scoped(BottomNavItem.Collection)
    val playlistRoutes = Routes.scoped(BottomNavItem.Playlists)
    val playerRoutes = Routes.scoped(BottomNavItem.NowPlaying)
    val snackbarHostState = LocalSnackbarHostState.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val onPlaylistAdded: (PlaylistAddOutcome) -> Unit = { outcome ->
        navController.popBackStack()
        val message = when {
            outcome.addedCount == 0 -> resources.getString(
                R.string.playlist_songs_already_exist,
                outcome.duplicateCount
            )
            outcome.duplicateCount > 0 -> resources.getString(
                R.string.playlist_songs_added_with_duplicates,
                outcome.addedCount,
                outcome.duplicateCount
            )
            else -> resources.getString(R.string.playlist_songs_added, outcome.addedCount)
        }
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

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
                        navController.navigateWithinTopLevel(songsRoutes.songInfo(songId))
                    }
                )
            }
            songInfoDestination(navController, songsRoutes)
            metadataDestinations(navController, songsRoutes)
            playlistPickerDestination(navController, songsRoutes, onPlaylistAdded)
            settingsDestinations(navController)
        }

        navigation(
            route = BottomNavItem.Collection.route,
            startDestination = BottomNavItem.Collection.startDestination
        ) {
            composable(BottomNavItem.Collection.startDestination) {
                CollectionScreen(
                    onAlbumsClick = { navController.navigate(collectionRoutes.allAlbums) },
                    onAlbumArtistsClick = { navController.navigate(collectionRoutes.allArtists) },
                    onGenresClick = { navController.navigate(collectionRoutes.allGenres) },
                    onYearsClick = { navController.navigate(collectionRoutes.allYears) },
                    onSeriesClick = { navController.navigate(collectionRoutes.allSeries) }
                )
            }
            metadataDestinations(navController, collectionRoutes)
            playlistPickerDestination(navController, collectionRoutes, onPlaylistAdded)
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
                val playlistId = backStackEntry.arguments?.getLong("playlistId")
                    ?: return@composable
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    onNavigateBack = { navController.popBackStack() },
                    onAddSongsClick = { navController.navigate(Routes.selectSongs(playlistId)) },
                    onAddToPlaylist = { songIds ->
                        navController.navigate(playlistRoutes.playlistPicker(songIds))
                    }
                )
            }

            composable(
                route = "select_songs/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId")
                    ?: return@composable
                val playlistDetailBackStackEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.playlistDetail(playlistId))
                }
                val playlistDetailViewModel: PlaylistDetailViewModel =
                    hiltViewModel(playlistDetailBackStackEntry)
                SelectSongsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSongsSelected = { songs ->
                        playlistDetailViewModel.queueSongAdditions(songs)
                        navController.popBackStack()
                    }
                )
            }
            playlistPickerDestination(navController, playlistRoutes, onPlaylistAdded)
        }

        navigation(
            route = BottomNavItem.NowPlaying.route,
            startDestination = BottomNavItem.NowPlaying.startDestination
        ) {
            composable(BottomNavItem.NowPlaying.startDestination) {
                NowPlayingScreen(
                    onSongInfoClick = { songId ->
                        navController.navigateWithinTopLevel(playerRoutes.songInfo(songId))
                    },
                    onAddToPlaylist = { songIds ->
                        navController.navigateWithinTopLevel(
                            playerRoutes.playlistPicker(songIds)
                        )
                    },
                    uiResetToken = playerUiResetToken
                )
            }
            songInfoDestination(navController, playerRoutes)
            metadataDestinations(navController, playerRoutes)
            playlistPickerDestination(navController, playerRoutes, onPlaylistAdded)
        }
    }
}

private fun NavGraphBuilder.songInfoDestination(
    navController: NavHostController,
    routes: ScopedRoutes
) {
    composable(
        route = routes.songInfoPattern,
        arguments = listOf(navArgument("songId") { type = NavType.StringType })
    ) { backStackEntry ->
        val songId = backStackEntry.arguments?.getString("songId") ?: return@composable
        SongInfoScreen(
            songId = songId,
            onNavigateBack = { navController.popBackStack() },
            onAlbumClick = { album, albumArtist, year ->
                navController.navigate(routes.albumDetail(album, albumArtist, year))
            },
            onAllAlbumArtistsClick = { navController.navigate(routes.allArtists) },
            onAlbumArtistClick = { artist ->
                navController.navigate(routes.artistDetail(artist))
            },
            onAllAlbumsClick = { navController.navigate(routes.allAlbums) },
            onAllYearsClick = { navController.navigate(routes.allYears) },
            onAllGenresClick = { navController.navigate(routes.allGenres) },
            onAllSeriesClick = { navController.navigate(routes.allSeries) },
            onYearClick = { year -> navController.navigate(routes.yearDetail(year)) },
            onGenreClick = { genre -> navController.navigate(routes.genreDetail(genre)) },
            onSeriesClick = { seriesName ->
                navController.navigate(routes.seriesDetail(seriesName))
            }
        )
    }
}

private fun NavGraphBuilder.metadataDestinations(
    navController: NavHostController,
    routes: ScopedRoutes
) {
    composable(
        route = routes.genreDetailPattern,
        arguments = listOf(navArgument("genreName") { type = NavType.StringType })
    ) { backStackEntry ->
        val genreName = backStackEntry.arguments?.getString("genreName").orEmpty()
        GenreDetailScreen(
            genre = genreName,
            onNavigateBack = { navController.popBackStack() },
            onArtistClick = { artist -> navController.navigate(routes.artistDetail(artist)) },
            onAlbumClick = { album, albumArtist, year ->
                navController.navigate(routes.albumDetail(album, albumArtist, year))
            },
            onAddToPlaylist = { songIds ->
                navController.navigate(routes.playlistPicker(songIds))
            }
        )
    }

    composable(
        route = routes.yearDetailPattern,
        arguments = listOf(navArgument("year") { type = NavType.IntType })
    ) { backStackEntry ->
        val year = backStackEntry.arguments?.getInt("year") ?: 0
        YearDetailScreen(
            year = year,
            onNavigateBack = { navController.popBackStack() },
            onArtistClick = { artist -> navController.navigate(routes.artistDetail(artist)) },
            onAlbumClick = { album, albumArtist, albumYear ->
                navController.navigate(routes.albumDetail(album, albumArtist, albumYear))
            },
            onAllYearsClick = { navController.navigate(routes.allYears) },
            onAddToPlaylist = { songIds ->
                navController.navigate(routes.playlistPicker(songIds))
            }
        )
    }

    composable(
        route = routes.decadeDetailPattern,
        arguments = listOf(navArgument("decade") { type = NavType.IntType })
    ) { backStackEntry ->
        val decade = backStackEntry.arguments?.getInt("decade") ?: 0
        DecadeDetailScreen(
            decade = decade,
            onNavigateBack = { navController.popBackStack() },
            onArtistClick = { artist -> navController.navigate(routes.artistDetail(artist)) },
            onAlbumClick = { album, albumArtist, year ->
                navController.navigate(routes.albumDetail(album, albumArtist, year))
            },
            onAddToPlaylist = { songIds ->
                navController.navigate(routes.playlistPicker(songIds))
            }
        )
    }

    composable(
        route = routes.artistDetailPattern,
        arguments = listOf(navArgument("artistName") { type = NavType.StringType })
    ) { backStackEntry ->
        val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
        ArtistDetailScreen(
            artistName = artistName,
            onNavigateBack = { navController.popBackStack() },
            onAlbumClick = { album, albumArtist, year ->
                navController.navigate(routes.albumDetail(album, albumArtist, year))
            },
            onBioClick = { navController.navigate(routes.artistBiography(artistName)) },
            onAddToPlaylist = { songIds ->
                navController.navigate(routes.playlistPicker(songIds))
            }
        )
    }

    composable(
        route = routes.artistBiographyPattern,
        arguments = listOf(navArgument("artistName") { type = NavType.StringType })
    ) { backStackEntry ->
        val artistName = backStackEntry.arguments?.getString("artistName").orEmpty()
        val artistEntry = remember(backStackEntry) {
            navController.getBackStackEntry(routes.artistDetail(artistName))
        }
        val artistViewModel: ArtistDetailViewModel = hiltViewModel(artistEntry)
        ArtistBiographyScreen(
            artistName = artistName,
            onNavigateBack = { navController.popBackStack() },
            viewModel = artistViewModel
        )
    }

    composable(routes.allArtists) {
        ArtistListScreen(
            onNavigateBack = { navController.popBackStack() },
            onArtistClick = { artist -> navController.navigate(routes.artistDetail(artist)) }
        )
    }

    composable(routes.allAlbums) {
        AlbumListScreen(
            onNavigateBack = { navController.popBackStack() },
            onAlbumClick = { albumName, albumArtist, year ->
                navController.navigate(routes.albumDetail(albumName, albumArtist, year))
            }
        )
    }

    composable(routes.allYears) {
        YearListScreen(
            onNavigateBack = { navController.popBackStack() },
            onYearClick = { year -> navController.navigate(routes.yearDetail(year)) },
            onDecadeClick = { decade -> navController.navigate(routes.decadeDetail(decade)) }
        )
    }

    composable(routes.allGenres) {
        GenreListScreen(
            onNavigateBack = { navController.popBackStack() },
            onGenreClick = { genre -> navController.navigate(routes.genreDetail(genre)) }
        )
    }

    composable(routes.allSeries) {
        SeriesListScreen(
            onNavigateBack = { navController.popBackStack() },
            onSeriesClick = { seriesName ->
                navController.navigate(routes.seriesDetail(seriesName))
            }
        )
    }

    composable(
        route = routes.albumDetailPattern,
        arguments = listOf(
            navArgument("albumName") { type = NavType.StringType },
            navArgument("albumArtistName") { type = NavType.StringType },
            navArgument("albumYear") { type = NavType.IntType }
        )
    ) { backStackEntry ->
        val albumName = backStackEntry.arguments?.getString("albumName").orEmpty()
        val albumArtistName = backStackEntry.arguments
            ?.getString("albumArtistName")
            .orEmpty()
        val albumYear = backStackEntry.arguments?.getInt("albumYear") ?: 0
        AlbumDetailScreen(
            albumName = albumName,
            albumArtistName = albumArtistName,
            albumYear = albumYear,
            onNavigateBack = { navController.popBackStack() },
            onArtistClick = { artist -> navController.navigate(routes.artistDetail(artist)) },
            onSeriesClick = { seriesName ->
                navController.navigate(routes.seriesDetail(seriesName))
            },
            onYearClick = { year -> navController.navigate(routes.yearDetail(year)) },
            onAddToPlaylist = { songIds ->
                navController.navigate(routes.playlistPicker(songIds))
            }
        )
    }

    composable(
        route = routes.seriesDetailPattern,
        arguments = listOf(navArgument("seriesName") { type = NavType.StringType })
    ) { backStackEntry ->
        val seriesName = backStackEntry.arguments?.getString("seriesName").orEmpty()
        SeriesDetailScreen(
            seriesName = seriesName,
            onNavigateBack = { navController.popBackStack() },
            onAlbumClick = { album, albumArtist, year ->
                navController.navigate(routes.albumDetail(album, albumArtist, year))
            },
            onAddToPlaylist = { songIds ->
                navController.navigate(routes.playlistPicker(songIds))
            },
            onAllSeriesClick = { navController.navigate(routes.allSeries) }
        )
    }
}

private fun NavGraphBuilder.playlistPickerDestination(
    navController: NavHostController,
    routes: ScopedRoutes,
    onPlaylistAdded: (PlaylistAddOutcome) -> Unit
) {
    composable(
        route = routes.playlistPickerPattern,
        arguments = listOf(navArgument("songIds") { type = NavType.StringType })
    ) {
        PlaylistPickerScreen(
            onNavigateBack = { navController.popBackStack() },
            onPlaylistSelected = onPlaylistAdded
        )
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
        GeneralSettingsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(Routes.ARTIST_DATA_SOURCE) {
        ArtistDataSourceScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(Routes.SOURCE_SETTINGS) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onAddSource = { navController.navigate(Routes.addSource(null)) },
            onEditSource = { id -> navController.navigate(Routes.addSource(id)) }
        )
    }

    composable(Routes.ABOUT) {
        AboutScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "${Routes.ADD_SOURCE}?sourceId={sourceId}",
        arguments = listOf(
            navArgument("sourceId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
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
