package com.schwanitz.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.createGraph
import androidx.navigation.navigation
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
                    testGraph(BottomNavItem.Songs, SONG_INFO)
                    testGraph(BottomNavItem.Collection, ALBUM_DETAIL)
                    testGraph(BottomNavItem.Playlists, PLAYLIST_PICKER)
                    testGraph(BottomNavItem.NowPlaying)
                }
            }
        }
    }

    @Test
    fun crossGraphLinksKeepEveryTopLevelBackStackIsolated() = runOnMain {
        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        assertDestination(BottomNavItem.NowPlaying.startDestination, BottomNavItem.NowPlaying)

        navController.navigateToTopLevelDestination(BottomNavItem.Songs, SONG_INFO)
        assertDestination(SONG_INFO, BottomNavItem.Songs)

        navController.navigateToTopLevelDestination(BottomNavItem.Collection, ALBUM_DETAIL)
        assertDestination(ALBUM_DETAIL, BottomNavItem.Collection)

        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        assertDestination(BottomNavItem.NowPlaying.startDestination, BottomNavItem.NowPlaying)

        navController.navigateToTopLevel(BottomNavItem.Songs)
        assertDestination(SONG_INFO, BottomNavItem.Songs)

        navController.navigateToTopLevel(BottomNavItem.Songs)
        assertDestination(BottomNavItem.Songs.startDestination, BottomNavItem.Songs)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        assertDestination(ALBUM_DETAIL, BottomNavItem.Collection)
    }

    @Test
    fun playlistPickerBelongsOnlyToPlaylistsBackStack() = runOnMain {
        navController.navigateToTopLevelDestination(BottomNavItem.Collection, ALBUM_DETAIL)
        navController.navigateToTopLevelDestination(BottomNavItem.Playlists, PLAYLIST_PICKER)
        assertDestination(PLAYLIST_PICKER, BottomNavItem.Playlists)

        navController.navigateToTopLevel(BottomNavItem.Collection)
        assertDestination(ALBUM_DETAIL, BottomNavItem.Collection)

        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        navController.navigateToTopLevelDestination(BottomNavItem.Playlists, PLAYLIST_PICKER)
        assertDestination(PLAYLIST_PICKER, BottomNavItem.Playlists)

        navController.navigateToTopLevel(BottomNavItem.NowPlaying)
        assertDestination(BottomNavItem.NowPlaying.startDestination, BottomNavItem.NowPlaying)
    }

    private fun assertDestination(route: String, owner: BottomNavItem) {
        assertEquals(route, navController.currentDestination?.route)
        assertTrue(
            navController.currentDestination?.hierarchy?.any { it.route == owner.route } == true
        )
    }

    private fun androidx.navigation.NavGraphBuilder.testGraph(
        item: BottomNavItem,
        childRoute: String? = null
    ) {
        navigation(startDestination = item.startDestination, route = item.route) {
            composable(item.startDestination) {}
            childRoute?.let { composable(it) {} }
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
        const val SONG_INFO = "song_info/song-1"
        const val ALBUM_DETAIL = "album_detail/album/artist/2026"
        const val PLAYLIST_PICKER = "playlist_picker/song-1"
    }
}
