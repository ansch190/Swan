package com.schwanitz.swan

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.util.Log

class GenrePagerAdapter(
    fragmentActivity: FragmentActivity,
    private val genre: String,
    private val highlightSongUri: String? = null
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        Log.d("GenrePagerAdapter", "Creating fragment for position: $position, genre: $genre, highlightSongUri: $highlightSongUri")
        return when (position) {
            0 -> DiscFragment.newInstance("1", genre, highlightSongUri, filterType = "genre")
            1 -> GenreArtistsFragment.newInstance(genre)
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}