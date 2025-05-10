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
import com.bumptech.glide.Glide
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

                // Lade das Albumcover und alle Bilder für den Klick
                loadAlbumArtwork(filteredFiles)

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

    private suspend fun loadAlbumArtwork(files: List<MusicFile>) {
        if (files.isEmpty()) {
            withContext(Dispatchers.Main) {
                binding.albumArtwork.visibility = View.GONE
            }
            return
        }

        val firstFile = files.first()
        val metadataExtractor = MetadataExtractor(this@SongsActivity)
        val metadata = withContext(Dispatchers.IO) {
            try {
                metadataExtractor.extractMetadata(firstFile.uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load metadata for ${firstFile.uri}: ${e.message}", e)
                null
            }
        }

        val artworkBytes = withContext(Dispatchers.IO) {
            try {
                metadataExtractor.getArtworkBytes(firstFile.uri, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load artwork for ${firstFile.uri}: ${e.message}", e)
                null
            }
        }

        val allArtworks = mutableListOf<ByteArray>()
        if (metadata != null && metadata.artworkCount > 0) {
            withContext(Dispatchers.IO) {
                for (index in 0 until metadata.artworkCount) {
                    try {
                        metadataExtractor.getArtworkBytes(firstFile.uri, index)?.let { bytes ->
                            if (bytes.isNotEmpty()) {
                                allArtworks.add(bytes)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load artwork at index $index for ${firstFile.uri}: ${e.message}", e)
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                Glide.with(this@SongsActivity)
                    .load(artworkBytes)
                    .error(android.R.drawable.ic_menu_close_clear_cancel)
                    .into(binding.albumArtwork)
                binding.albumArtwork.visibility = View.VISIBLE

                // Füge OnClickListener hinzu, um ImageViewerDialogFragment zu öffnen
                binding.albumArtwork.setOnClickListener {
                    if (allArtworks.isNotEmpty()) {
                        ImageViewerDialogFragment.newInstance(ArrayList(allArtworks), 0)
                            .show(supportFragmentManager, "ImageViewerDialog")
                    }
                }
            } else {
                binding.albumArtwork.visibility = View.GONE
            }
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
        // AlbumArtwork-Sichtbarkeit wird in loadAlbumArtwork gehandhabt
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
        // AlbumArtwork-Sichtbarkeit wird in loadAlbumArtwork gehandhabt
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