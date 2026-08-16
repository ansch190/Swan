package com.schwanitz.ui.navigation

import com.schwanitz.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavItemTest {

    @Test
    fun `tabs have the specified order and labels`() {
        assertEquals(
            listOf(
                BottomNavItem.Songs,
                BottomNavItem.Collection,
                BottomNavItem.Playlists,
                BottomNavItem.NowPlaying
            ),
            BottomNavItem.items
        )
        assertEquals(R.string.bottom_songs, BottomNavItem.items[0].titleRes)
        assertEquals(R.string.bottom_collection, BottomNavItem.items[1].titleRes)
        assertEquals(R.string.bottom_playlists, BottomNavItem.items[2].titleRes)
        assertEquals(R.string.bottom_now_playing, BottomNavItem.items[3].titleRes)
    }

    @Test
    fun `each tab owns a distinct graph and start destination`() {
        assertEquals(BottomNavItem.items.size, BottomNavItem.items.map { it.route }.toSet().size)
        assertEquals(BottomNavItem.items.size, BottomNavItem.items.map { it.startDestination }.toSet().size)
        assertTrue(BottomNavItem.items.none { it.route == it.startDestination })
        assertEquals(Routes.COLLECTION, BottomNavItem.Collection.startDestination)
    }
}
