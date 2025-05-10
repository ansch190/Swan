package com.schwanitz.swan

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class DiscPagerAdapter(
    activity: FragmentActivity,
    private val discNumbers: List<String>,
    private val albumName: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = discNumbers.size

    override fun createFragment(position: Int): Fragment {
        return DiscFragment.newInstance(discNumbers[position], albumName)
    }
}