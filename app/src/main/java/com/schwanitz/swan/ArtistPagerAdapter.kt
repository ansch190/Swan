package com.schwanitz.swan

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.util.Log

class ArtistPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val artistName: String,
    private val highlightSongUri: String? = null
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        Log.d("ArtistPagerAdapter", "Creating fragment for position: $position, artist: $artistName, highlightSongUri: $highlightSongUri")
        return when (position) {
            0 -> DiscFragment.newInstance("1", artistName, highlightSongUri, filterType = "artist") // Korrigierter filterType
            1 -> ArtistAlbumsFragment.newInstance(artistName)
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}