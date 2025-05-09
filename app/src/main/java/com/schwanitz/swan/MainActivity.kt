package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var musicAdapter: MusicFileAdapter
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "MainActivity"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicPlaybackBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
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

        musicAdapter = MusicFileAdapter(
            musicFiles = emptyList(),
            onItemClick = { uri ->
                musicService?.play(uri)
            },
            onShowMetadata = { musicFile ->
                Log.d(TAG, "Showing metadata for file: ${musicFile.name}")
                val position = musicAdapter.filteredFiles.indexOf(musicFile)
                if (position >= 0) {
                    MetadataFragment.newInstance(musicAdapter.filteredFiles, position)
                        .show(supportFragmentManager, "MetadataFragment")
                }
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = musicAdapter
            registerForContextMenu(this)
        }

        viewModel.musicFiles.observe(this) { files ->
            Log.d(TAG, "Music files updated, count: ${files.size}")
            musicAdapter.updateFiles(files)
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                musicAdapter.filter(newText)
                return true
            }
        })

        binding.playButton.setOnClickListener {
            musicService?.play(musicAdapter.filteredFiles.getOrNull(musicAdapter.selectedPosition)?.uri ?: return@setOnClickListener)
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

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.context_info -> {
                val position = musicAdapter.selectedPosition
                if (position != RecyclerView.NO_POSITION) {
                    Log.d(TAG, "Context menu: Showing metadata for file at position: $position")
                    MetadataFragment.newInstance(musicAdapter.filteredFiles, position)
                        .show(supportFragmentManager, "MetadataFragment")
                    true
                } else {
                    Log.w(TAG, "Context menu: Invalid position $position")
                    false
                }
            }
            else -> super.onContextItemSelected(item)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
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
    }
}