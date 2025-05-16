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
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.FilterEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.databinding.ActivityLibraryBinding
import com.schwanitz.swan.service.MusicPlaybackService
import com.schwanitz.swan.ui.fragment.FilterFragment
import com.schwanitz.swan.ui.fragment.FilterSettingsFragment
import com.schwanitz.swan.ui.fragment.LibraryPathsFragment
import com.schwanitz.swan.ui.fragment.PlaylistsFragment
import com.schwanitz.swan.ui.fragment.SettingsFragment
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private lateinit var viewModel: MainViewModel
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "LibraryActivity"
    private val NOTIFICATION_PERMISSION_CODE = 100
    private val STORAGE_PERMISSION_CODE = 101
    private var filters = listOf<FilterEntity>()
    private val _searchQuery = MutableStateFlow<String?>(null)
    val searchQuery = _searchQuery.asStateFlow()
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
        binding = ActivityLibraryBinding.inflate(layoutInflater)
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
                    Log.d(TAG, "Already in library, closing drawer")
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_playlists -> {
                    Log.d(TAG, "Opening playlists")
                    PlaylistsFragment().show(supportFragmentManager, "PlaylistsFragment")
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

        viewModel = ViewModelProvider(this, MainViewModelFactory(this, MusicRepository(this))).get(MainViewModel::class.java)

        // Prüfe, ob Bibliothekspfade vorhanden sind
        lifecycleScope.launch {
            val libraryPaths = AppDatabase.getDatabase(this@LibraryActivity).libraryPathDao().getAllPaths().first()
            if (libraryPaths.isEmpty()) {
                Log.d(TAG, "No library paths defined, showing message and navigating to LibraryPathsFragment")
                Toast.makeText(
                    this@LibraryActivity,
                    R.string.no_library_paths_message,
                    Toast.LENGTH_LONG
                ).show()
                LibraryPathsFragment().show(supportFragmentManager, "LibraryPathsFragment")
            } else {
                // Stelle sicher, dass mindestens ein Filter vorhanden ist
                val filtersFromDb = AppDatabase.getDatabase(this@LibraryActivity).filterDao().getAllFilters().first()
                if (filtersFromDb.isEmpty()) {
                    Log.d(TAG, "No filters found, adding default filters")
                    viewModel.addFilter("title", getString(R.string.filter_by_title))
                    viewModel.addFilter("artist", getString(R.string.filter_by_artist))
                    viewModel.addFilter("album", getString(R.string.filter_by_album))
                }
                // Beobachte Filter aus der Datenbank
                AppDatabase.getDatabase(this@LibraryActivity).filterDao().getAllFilters().collectLatest { filterList ->
                    Log.d(TAG, "Loaded filters: ${filterList.map { it.displayName }}")
                    filters = filterList
                    setupTabs()
                }
                // Beobachte Musikdateien, um Tabs nach Scan zu aktualisieren
                viewModel.musicFiles.observe(this@LibraryActivity) { files ->
                    Log.d(TAG, "Music files updated: ${files.size} files")
                    setupTabs() // Initialisiert die Tabs mit den Filtern
                    // Explizit FilterFragmente aktualisieren
                    (binding.viewPager.adapter as? FilterPagerAdapter)?.let { adapter ->
                        for (i in 0 until adapter.itemCount) {
                            val fragment = supportFragmentManager.findFragmentByTag("f$i") as? FilterFragment
                            fragment?.filter(searchQuery.value) // Aktualisiert jedes Fragment mit den neuen Daten
                            if (fragment == null) {
                                Log.w(TAG, "Fragment not found for tab $i during music files update")
                            }
                        }
                    }
                }
            }
        }

        // Initialisiere SearchView
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                Log.d(TAG, "Search query submitted: $query")
                _searchQuery.value = query
                searchJob?.cancel()
                applyFilter(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                Log.d(TAG, "Search query changed: $newText")
                _searchQuery.value = newText
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // Warte 300ms, um schnelles Tippen zu debouncen
                    applyFilter(newText)
                }
                return true
            }
        })

        binding.playButton.setOnClickListener {
            // Wiedergabe wird später angepasst
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

    private fun applyFilter(query: String?) {
        val currentFragment = supportFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}") as? FilterFragment
        if (currentFragment != null) {
            Log.d(TAG, "Applying filter to fragment: $query")
            currentFragment.filter(query)
        } else {
            Log.w(TAG, "No fragment found for current tab: ${binding.viewPager.currentItem}")
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

    override fun onCreateContextMenu(menu: ContextMenu, v: android.view.View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu, menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Setze den aktuellen Suchbegriff im SearchView
        searchQuery.value?.let { query ->
            Log.d(TAG, "Restoring search query in SearchView: $query")
            binding.searchView.setQuery(query, false)
            binding.searchView.isIconified = false
        }
        return true
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.context_info -> {
                // Wird später angepasst
                false
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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
        // Stoppe den MusicPlaybackService
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
                Toast.makeText(this, "Required permissions not granted, some features may not work", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupTabs() {
        // ViewPager und Adapter einrichten
        binding.viewPager.adapter = FilterPagerAdapter(this, filters)
        Log.d(TAG, "ViewPager adapter set with ${filters.size} filters")

        // TabLayout mit ViewPager verbinden
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = filters.getOrNull(position)?.displayName ?: ""
        }.attach()

        // Long-Click-Listener für jeden Tab setzen
        for (i in 0 until binding.tabLayout.tabCount) {
            val tab = binding.tabLayout.getTabAt(i)
            tab?.view?.setOnLongClickListener {
                Log.d(TAG, "Long click on tab $i, opening FilterSettingsFragment")
                FilterSettingsFragment().show(supportFragmentManager, "FilterSettingsFragment")
                true // Ereignis als behandelt markieren
            }
        }

        // Tab-Wechsel-Listener, um den Suchbegriff anzuwenden
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { position ->
                    Log.d(TAG, "Tab selected: $position, applying search query: ${searchQuery.value}")
                    val currentFragment = supportFragmentManager.findFragmentByTag("f$position") as? FilterFragment
                    currentFragment?.filter(searchQuery.value)
                    if (currentFragment == null) {
                        Log.w(TAG, "Fragment not found for tab $position on selection")
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Wende den aktuellen Suchbegriff auf den initialen Tab an
        lifecycleScope.launch {
            // Warte kurz, um sicherzustellen, dass Fragmente initialisiert sind
            delay(100)
            val initialFragment = supportFragmentManager.findFragmentByTag("f${binding.viewPager.currentItem}") as? FilterFragment
            if (initialFragment != null) {
                Log.d(TAG, "Applying initial search query to tab ${binding.viewPager.currentItem}: ${searchQuery.value}")
                initialFragment.filter(searchQuery.value)
            } else {
                Log.w(TAG, "Initial fragment not found for tab ${binding.viewPager.currentItem} after delay")
            }
        }
    }
}

class FilterPagerAdapter(
    activity: FragmentActivity,
    private val filters: List<FilterEntity>
) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = filters.size
    override fun createFragment(position: Int): Fragment {
        return FilterFragment.newInstance(filters[position].criterion)
    }
}