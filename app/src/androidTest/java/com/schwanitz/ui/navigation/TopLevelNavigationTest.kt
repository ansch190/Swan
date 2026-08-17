package com.schwanitz.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopLevelNavigationTest {
    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        runOnMain {
            navController = TestNavHostController(
                InstrumentationRegistry.getInstrumentation().targetContext
            ).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                setViewModelStore(ViewModelStore())
                setLifecycleOwner(ResumedLifecycleOwner())
                graph = createGraph(startDestination = BottomNavItem.Songs.route) {
                    testGraph(
                        BottomNavItem.Songs,
                        SONGS_INFO,
                        SONGS_ALBUM,
                        SONGS_PICKER
                    )
                    testGraph(
                        BottomNavItem.Collection,
                        COLLECTION_ALBUM,
                        COLLECTION_PICKER
                    )
                    testGraph(
                        BottomNavItem.Playlists,
                        PLAYLIST_DETAIL,
                        PLAYLISTS_PICKER
                    )
                    testGraph(
                        BottomNavItem.NowPlaying,
                        PLAYER_INFO,
                        PLAYER_ALBUM,
                        PLAYER_PICKER
                    )
                }
            }
        }
    }

    @Test
    fun sameContentCanHaveIndependentSongsAndCollectionHistories() = runOnMain {
        navController.navigateWithinTopLevel(SONGS_INFO)
        navController.navigateWithinTopLevel(SONGS_ALBUM)
        assertDestination(SONGS_ALBUM, BottomNavItem.Songs)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        navController.navigateWithinTopLevel(COLLECTION_ALBUM)
        assertDestination(COLLECTION_ALBUM, BottomNavItem.Collection)

        navController.navigateToTopLevel(BottomNavItem.Songs)
        assertDestination(SONGS_ALBUM, BottomNavItem.Songs)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        assertDestination(COLLECTION_ALBUM, BottomNavItem.Collection)
    }

    @Test
    fun playerDetailsAreRestoredNormallyAndDiscardedForFreshPlayback() = runOnMain {
        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        navController.navigateWithinTopLevel(PLAYER_INFO)
        navController.navigateWithinTopLevel(PLAYER_ALBUM)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        navController.navigateWithinTopLevel(COLLECTION_ALBUM)
        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        assertDestination(PLAYER_ALBUM, BottomNavItem.NowPlaying)

        assertFalse(navController.navigateToPlayerForExternalPlayback())
        assertDestination(PLAYER_ALBUM, BottomNavItem.NowPlaying)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        assertTrue(navController.navigateToPlayerForExternalPlayback())
        assertDestination(
            BottomNavItem.NowPlaying.startDestination,
            BottomNavItem.NowPlaying
        )

        navController.navigateToTopLevel(BottomNavItem.Collection)
        assertDestination(COLLECTION_ALBUM, BottomNavItem.Collection)
        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        assertDestination(
            BottomNavItem.NowPlaying.startDestination,
            BottomNavItem.NowPlaying
        )
    }

    @Test
    fun reselectingAnActiveTabReturnsOnlyThatTabToItsRoot() = runOnMain {
        navController.navigateWithinTopLevel(SONGS_INFO)
        navController.returnToRoot(BottomNavItem.Songs)
        assertDestination(BottomNavItem.Songs.startDestination, BottomNavItem.Songs)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        navController.navigateWithinTopLevel(COLLECTION_ALBUM)
        navController.returnToRoot(BottomNavItem.Collection)
        assertDestination(
            BottomNavItem.Collection.startDestination,
            BottomNavItem.Collection
        )
    }

    @Test
    fun playlistPickerStaysInsideItsOriginatingTab() = runOnMain {
        navController.navigateToTopLevel(BottomNavItem.Collection)
        navController.navigateWithinTopLevel(COLLECTION_ALBUM)
        navController.navigateWithinTopLevel(COLLECTION_PICKER)
        assertDestination(COLLECTION_PICKER, BottomNavItem.Collection)
        navController.popBackStack()
        assertDestination(COLLECTION_ALBUM, BottomNavItem.Collection)

        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        navController.navigateWithinTopLevel(PLAYER_PICKER)
        assertDestination(PLAYER_PICKER, BottomNavItem.NowPlaying)

        navController.navigateToTopLevel(BottomNavItem.Playlists)
        navController.navigateWithinTopLevel(PLAYLIST_DETAIL)
        navController.navigateWithinTopLevel(PLAYLISTS_PICKER)
        assertDestination(PLAYLISTS_PICKER, BottomNavItem.Playlists)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        assertDestination(COLLECTION_ALBUM, BottomNavItem.Collection)
    }

    private fun assertDestination(route: String, owner: BottomNavItem) {
        assertEquals(route, navController.currentDestination?.route)
        assertTrue(
            navController.currentDestination?.hierarchy?.any { it.route == owner.route } == true
        )
    }

    private fun androidx.navigation.NavGraphBuilder.testGraph(
        item: BottomNavItem,
        vararg childRoutes: String
    ) {
        navigation(startDestination = item.startDestination, route = item.route) {
            composable(item.startDestination) {}
            childRoutes.forEach { route -> composable(route) {} }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle = registry
    }

    private companion object {
        const val SONGS_INFO = "songs_graph/song_info/song-1"
        const val SONGS_ALBUM = "songs_graph/album_detail/album/artist/2026"
        const val SONGS_PICKER = "songs_graph/playlist_picker/song-1"
        const val COLLECTION_ALBUM = "collection_graph/album_detail/album/artist/2026"
        const val COLLECTION_PICKER = "collection_graph/playlist_picker/song-1"
        const val PLAYLIST_DETAIL = "playlist_detail/7"
        const val PLAYLISTS_PICKER = "playlists_graph/playlist_picker/song-1"
        const val PLAYER_INFO = "now_playing_graph/song_info/song-1"
        const val PLAYER_ALBUM = "now_playing_graph/album_detail/album/artist/2026"
        const val PLAYER_PICKER = "now_playing_graph/playlist_picker/song-1"
    }
}
