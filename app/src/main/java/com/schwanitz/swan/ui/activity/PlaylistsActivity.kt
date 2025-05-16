package com.schwanitz.swan.ui.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.schwanitz.swan.R
import com.schwanitz.swan.databinding.ActivityPlaylistsBinding
import com.schwanitz.swan.service.MusicPlaybackService
import com.schwanitz.swan.ui.fragment.LibraryPathsFragment
import com.schwanitz.swan.ui.fragment.PlaylistsListFragment
import com.schwanitz.swan.ui.fragment.PlaceholderFragment
import com.schwanitz.swan.ui.fragment.SettingsFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlaylistsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistsBinding
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "PlaylistsActivity"
    private val NOTIFICATION_PERMISSION_CODE = 100
    private val STORAGE_PERMISSION_CODE = 101
    private val _searchQuery = MutableStateFlow<String?>(null)
    private val searchQuery = _searchQuery.asStateFlow()
    private var searchJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicPlaybackBinder
            musicService = binder.getService()
            isBound = true
            Log.d(TAG, "MusicPlaybackService bound")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            musicService = null
            Log.d(TAG, "MusicPlaybackService unbound")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "Activity created, status bar flags: ${window.decorView.systemUiVisibility}")

        // Request permissions
        requestPermissions()

        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_library -> {
                    Log.d(TAG, "Navigating to library")
                    startActivity(Intent(this, LibraryActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_playlists -> {
                    Log.d(TAG, "Already in playlists, closing drawer")
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_settings -> {
                    Log.d(TAG, "Opening settings")
                    SettingsFragment().show(supportFragmentManager, "SettingsFragment")
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }

        // Setup tabs
        setupTabs()

        // Initialize SearchView
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d(TAG, "Search query submitted: $query")
                _searchQuery.value = query
                applySearchQuery(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(TAG, "Search query changed: $newText")
                _searchQuery.value = newText
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Debounce für schnelles Tippen
                    applySearchQuery(newText)
                }
                return true
            }
        })

        // Stelle sicher, dass der aktuelle Suchbegriff angewendet wird
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                Log.d(TAG, "Tab selected: $position, applying search query: ${searchQuery.value}")
                applySearchQuery(searchQuery.value)
            }
        })

        // Initiale Anwendung des Suchbegriffs
        lifecycleScope.launch {
            delay(100) // Warte kurz, bis Fragmente initialisiert sind
            applySearchQuery(searchQuery.value)
        }

        // Initialize buttons (placeholders for now)
        binding.playButton.setOnClickListener {
            // To be implemented
        }
        binding.pauseButton.setOnClickListener {
            musicService?.pause()
        }
        binding.stopButton.setOnClickListener {
            musicService?.stop()
        }

        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun applySearchQuery(query: String?) {
        Log.d(TAG, "Applying search query to fragment: $query")
        val currentFragment = supportFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}") as? PlaylistsListFragment
        currentFragment?.updateSearchQuery(query)
        if (currentFragment == null && binding.viewPager.currentItem == 0) {
            Log.w(TAG, "No PlaylistsListFragment found for tab ${binding.viewPager.currentItem}")
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), STORAGE_PERMISSION_CODE)
        } else {
            Log.d(TAG, "All required permissions already granted")
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "Opening drawer, current state: ${if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) "open" else "closed"}")
                binding.drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        Intent(this, MusicPlaybackService::class.java).also { intent ->
            stopService(intent)
        }
        Log.d(TAG, "Service stopped, activity destroyed")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE || requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Required permissions granted")
            } else {
                Log.w(TAG, "Required permissions denied")
                android.widget.Toast.makeText(this, "Required permissions not granted, some features may not work", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupTabs() {
        val tabTitles = listOf(
            getString(R.string.playlists_tab_all),
            getString(R.string.playlists_tab_placeholder1),
            getString(R.string.playlists_tab_placeholder2)
        )
        binding.viewPager.adapter = PlaylistsPagerAdapter(this, tabTitles.size)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
        Log.d(TAG, "ViewPager adapter set with ${tabTitles.size} tabs")
    }
}

class PlaylistsPagerAdapter(
    activity: FragmentActivity,
    private val tabCount: Int
) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = tabCount

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PlaylistsListFragment()
            else -> PlaceholderFragment()
        }
    }
}