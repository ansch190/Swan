package com.schwanitz.swan

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import android.util.Log

class DiscPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val discNumbers: List<String>,
    private val albumName: String
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = discNumbers.size

    override fun createFragment(position: Int): Fragment {
        val discNumber = discNumbers[position]
        Log.d("DiscPagerAdapter", "Creating fragment for disc: $discNumber, album: $albumName")
        return DiscFragment.newInstance(discNumber, albumName)
    }
}