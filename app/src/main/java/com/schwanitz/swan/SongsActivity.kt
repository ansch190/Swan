package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.schwanitz.swan.databinding.ActivitySongsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private lateinit var viewModel: MainViewModel
    private var adapter: MusicFileAdapter? = null
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "SongsActivity"
    private val prefs by lazy {
        getSharedPreferences("swan_prefs", Context.MODE_PRIVATE)
    }

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
        binding = ActivitySongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this, MainViewModelFactory(this, MusicRepository(this))).get(MainViewModel::class.java)

        val criterion = intent.getStringExtra("criterion") ?: "title"
        val value = intent.getStringExtra("value") ?: ""
        supportActionBar?.title = value

        val isTabViewEnabled = prefs.getBoolean("tab_view_enabled", false)

        viewModel.musicFiles.observe(this) { files ->
            lifecycleScope.launch(Dispatchers.Default) {
                val filteredFiles = files.filter { file ->
                    when (criterion) {
                        "album" -> file.album?.equals(value, ignoreCase = true) ?: false
                        else -> false
                    }
                }

                val discNumbers = filteredFiles.mapNotNull { it.discNumber }.distinct().sorted()
                val hasDiscMetadata = discNumbers.isNotEmpty()

                if (isTabViewEnabled && hasDiscMetadata) {
                    withContext(Dispatchers.Main) {
                        setupTabView(discNumbers, value)
                    }
                } else {
                    val sortedFiles = if (filteredFiles.all { file ->
                            !file.discNumber.isNullOrBlank() && file.discNumber.toIntOrNull() != null &&
                                    !file.trackNumber.isNullOrBlank() && file.trackNumber.toIntOrNull() != null
                        }) {
                        filteredFiles.sortedWith(compareBy(
                            { it.discNumber?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE },
                            { it.trackNumber?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE }
                        ))
                    } else {
                        filteredFiles.sortedBy { it.name }
                    }
                    withContext(Dispatchers.Main) {
                        setupListView(sortedFiles)
                    }
                }
            }
        }

        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun setupTabView(discNumbers: List<String>, albumName: String) {
        binding.viewPager.adapter = DiscPagerAdapter(this, discNumbers, albumName)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = "CD ${position + 1}"
        }.attach()
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.songsRecyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
    }

    private fun setupListView(filteredFiles: List<MusicFile>) {
        adapter = MusicFileAdapter(
            musicFiles = filteredFiles,
            onItemClick = { uri ->
                Log.d(TAG, "Playing file with URI: $uri")
                musicService?.play(uri)
            }
        )
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SongsActivity)
            adapter = this@SongsActivity.adapter
        }
        binding.songsRecyclerView.visibility = View.VISIBLE
        binding.emptyText.visibility = if (filteredFiles.isEmpty()) View.VISIBLE else View.GONE
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
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