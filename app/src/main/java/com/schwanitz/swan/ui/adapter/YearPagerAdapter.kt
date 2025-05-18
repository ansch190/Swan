package com.schwanitz.swan.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.util.Log
import com.schwanitz.swan.ui.fragment.DiscFragment
import com.schwanitz.swan.ui.fragment.YearAlbumsFragment

class YearPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val year: String,
    private val highlightSongUri: String? = null
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        Log.d("YearPagerAdapter", "Creating fragment for position: $position, year: $year, highlightSongUri: $highlightSongUri")
        return when (position) {
            0 -> DiscFragment.newInstance("1", year, highlightSongUri, filterType = "year")
            1 -> YearAlbumsFragment.newInstance(year)
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}