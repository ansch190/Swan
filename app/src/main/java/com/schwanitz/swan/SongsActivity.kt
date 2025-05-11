package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

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
        val highlightSongUri = intent.getStringExtra("highlight_song_uri") // URI des zu hervorhebenden Liedes
        Log.d(TAG, "Received intent: criterion=$criterion, value=$value, highlightSongUri=$highlightSongUri")
        supportActionBar?.title = value

        val isTabViewEnabled = prefs.getBoolean("tab_view_enabled", false)

        viewModel.musicFiles.observe(this) { files ->
            lifecycleScope.launch(Dispatchers.Default) {
                val filteredFiles = files.filter { file ->
                    when (criterion) {
                        "album" -> file.album?.equals(value, ignoreCase = true) ?: false
                        "artist" -> file.artist?.equals(value, ignoreCase = true) ?: false
                        else -> false
                    }
                }

                // Lade das Bild (Künstlerbild für "artist", Albumcover für "album")
                loadImage(criterion, value, filteredFiles)

                val discNumbers = filteredFiles
                    .mapNotNull { file ->
                        file.discNumber?.trim()?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()
                    }
                    .distinct()
                    .sorted()
                    .map { it.toString() }
                val hasDiscMetadata = discNumbers.isNotEmpty()
                Log.d(TAG, "Disc numbers: $discNumbers, hasDiscMetadata: $hasDiscMetadata")

                if (criterion == "album" && isTabViewEnabled && hasDiscMetadata) {
                    withContext(Dispatchers.Main) {
                        setupTabView(discNumbers, value, highlightSongUri, filteredFiles)
                    }
                } else {
                    val sortedFiles = if (criterion == "album" && filteredFiles.all { file ->
                            !file.discNumber.isNullOrBlank() && file.discNumber.toIntOrNull() != null &&
                                    !file.trackNumber.isNullOrBlank() && file.trackNumber.toIntOrNull() != null
                        }) {
                        filteredFiles.sortedWith(compareBy(
                            { it.discNumber?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE },
                            { it.trackNumber?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE }
                        ))
                    } else {
                        filteredFiles.sortedBy { it.title ?: it.name }
                    }
                    withContext(Dispatchers.Main) {
                        setupListView(sortedFiles, highlightSongUri)
                    }
                }
            }
        }

        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private suspend fun loadImage(criterion: String, value: String, files: List<MusicFile>) {
        if (files.isEmpty()) {
            withContext(Dispatchers.Main) {
                binding.albumArtwork.visibility = View.GONE
            }
            return
        }

        if (criterion == "artist") {
            // Lade Künstlerbild von TheAudioDB
            val artistImageRepository = ArtistImageRepository(AppDatabase.getDatabase(this@SongsActivity))
            val imageUrl = withContext(Dispatchers.IO) {
                try {
                    artistImageRepository.getArtistImageUrl(value)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load artist image for $value: ${e.message}", e)
                    null
                }
            }

            withContext(Dispatchers.Main) {
                if (imageUrl != null) {
                    Log.d(TAG, "Loading artist image for $value: $imageUrl")
                    // Lade das Bild mit Glide
                    Glide.with(this@SongsActivity)
                        .load(imageUrl)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(binding.albumArtwork)
                    binding.albumArtwork.visibility = View.VISIBLE

                    // Lade das Bild als ByteArray für den ImageViewerDialogFragment
                    val imageBytes = withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient()
                            val request = Request.Builder().url(imageUrl).build()
                            val bytes = client.newCall(request).execute().body?.bytes()
                            if (bytes != null) {
                                // Komprimiere das Bild, um Speicher zu sparen
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                val outputStream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                                outputStream.toByteArray()
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to download artist image for $value: ${e.message}", e)
                            null
                        }
                    }

                    // Öffne ImageViewerDialogFragment bei Klick
                    binding.albumArtwork.setOnClickListener {
                        if (imageBytes != null) {
                            Log.d(TAG, "Opening ImageViewerDialogFragment for artist image: $value")
                            ImageViewerDialogFragment.newInstance(ArrayList(listOf(imageBytes)), 0)
                                .show(supportFragmentManager, "ImageViewerDialog")
                        } else {
                            Log.w(TAG, "No image bytes available for $value")
                            android.widget.Toast.makeText(
                                this@SongsActivity,
                                "Failed to load artist image for viewing",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    binding.albumArtwork.visibility = View.GONE
                    android.widget.Toast.makeText(
                        this@SongsActivity,
                        "No artist image available for $value",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            // Lade Albumcover (für criterion == "album" oder andere)
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
                    Log.d(TAG, "Loading album artwork for ${firstFile.uri}")
                    Glide.with(this@SongsActivity)
                        .load(artworkBytes)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(binding.albumArtwork)
                    binding.albumArtwork.visibility = View.VISIBLE

                    // Öffne ImageViewerDialogFragment bei Klick
                    binding.albumArtwork.setOnClickListener {
                        if (allArtworks.isNotEmpty()) {
                            Log.d(TAG, "Opening ImageViewerDialogFragment for album artwork: ${firstFile.uri}")
                            ImageViewerDialogFragment.newInstance(ArrayList(allArtworks), 0)
                                .show(supportFragmentManager, "ImageViewerDialog")
                        }
                    }
                } else {
                    binding.albumArtwork.visibility = View.GONE
                }
            }
        }
    }

    private fun setupTabView(discNumbers: List<String>, albumName: String, highlightSongUri: String?, files: List<MusicFile>) {
        binding.viewPager.offscreenPageLimit = discNumbers.size // Cache alle Fragmente
        binding.viewPager.adapter = DiscPagerAdapter(this, discNumbers, albumName, highlightSongUri)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = "CD ${position + 1}"
        }.attach()
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.songsRecyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE

        // Scrolle zur richtigen Disc, wenn ein Lied hervorgehoben werden soll
        if (highlightSongUri != null) {
            val highlightUri = Uri.parse(highlightSongUri)
            val highlightFile = files.find { it.uri == highlightUri }
            if (highlightFile != null) {
                val highlightDiscNumber = highlightFile.discNumber?.trim()?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()?.toString()
                val discIndex = discNumbers.indexOf(highlightDiscNumber)
                if (discIndex >= 0) {
                    Log.d(TAG, "Navigating to disc index $discIndex for disc number $highlightDiscNumber")
                    binding.viewPager.setCurrentItem(discIndex, false)
                } else {
                    Log.w(TAG, "Disc number $highlightDiscNumber not found in discNumbers: $discNumbers")
                }
            } else {
                Log.w(TAG, "Highlight file not found for URI: $highlightSongUri")
            }
        }
    }

    private fun setupListView(filteredFiles: List<MusicFile>, highlightSongUri: String?) {
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

        // Scrolle zum Lied und hebe es hervor, wenn highlightSongUri gesetzt ist
        if (highlightSongUri != null) {
            val highlightUri = Uri.parse(highlightSongUri)
            val position = filteredFiles.indexOfFirst { it.uri == highlightUri }
            Log.d(TAG, "Attempting to highlight song in list view with URI: $highlightSongUri, position: $position")
            if (position >= 0) {
                binding.songsRecyclerView.layoutManager?.scrollToPosition(position)
                // Verzögere die Hervorhebung, um sicherzustellen, dass die RecyclerView gerendert ist
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(100) // 100ms Verzögerung
                    adapter?.highlightItem(binding.songsRecyclerView, position)
                    Log.d(TAG, "Highlighted song in list view at position $position for URI: $highlightSongUri")
                }
            } else {
                Log.w(TAG, "Highlight song not found in filtered files for list view: $highlightSongUri")
            }
        }
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