package com.schwanitz.swan.ui.activity

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
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.repository.ArtistImageRepository
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.databinding.ActivitySongsBinding
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.usecase.Metadata
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import com.schwanitz.swan.service.MusicPlaybackService
import com.schwanitz.swan.ui.adapter.ArtistPagerAdapter
import com.schwanitz.swan.ui.adapter.DiscPagerAdapter
import com.schwanitz.swan.ui.adapter.GenrePagerAdapter
import com.schwanitz.swan.ui.adapter.MusicFileAdapter
import com.schwanitz.swan.ui.adapter.YearPagerAdapter
import com.schwanitz.swan.ui.fragment.AddMultipleToPlaylistDialogFragment
import com.schwanitz.swan.ui.fragment.ImageViewerDialogFragment
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
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
    private var filteredFiles: List<MusicFile> = emptyList()

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

        binding.root.setPadding(0, getStatusBarHeight(), 0, 0)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                Log.d(TAG, "Toolbar bounds: top=${binding.toolbar.top}, bottom=${binding.toolbar.bottom}")
                Log.d(TAG, "AlbumArtwork bounds: top=${binding.albumArtwork.top}, bottom=${binding.albumArtwork.bottom}")
                Log.d(TAG, "TabLayout bounds: top=${binding.tabLayout.top}, bottom=${binding.tabLayout.bottom}")
                Log.d(TAG, "ViewPager bounds: top=${binding.viewPager.top}, bottom=${binding.viewPager.bottom}")
                Log.d(TAG, "RecyclerView bounds: top=${binding.songsRecyclerView.top}, bottom=${binding.songsRecyclerView.bottom}")
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })

        viewModel = ViewModelProvider(this, MainViewModelFactory(this, MusicRepository(this))).get(MainViewModel::class.java)

        val criterion = intent.getStringExtra("criterion") ?: "title"
        val value = intent.getStringExtra("value") ?: ""
        val highlightSongUri = intent.getStringExtra("highlight_song_uri")
        Log.d(TAG, "Received intent: criterion=$criterion, value=$value, highlightSongUri=$highlightSongUri")
        supportActionBar?.title = value

        viewModel.musicFiles.observe(this) { files ->
            lifecycleScope.launch(Dispatchers.Default) {
                filteredFiles = files.filter { file ->
                    when (criterion) {
                        "album" -> file.album?.equals(value, ignoreCase = true) ?: false
                        "artist" -> file.artist?.equals(value, ignoreCase = true) ?: false
                        "genre" -> file.genre?.equals(value, ignoreCase = true) ?: false
                        "year" -> file.year?.equals(value, ignoreCase = true) ?: false
                        else -> false
                    }
                }
                Log.d(TAG, "Filtered files for $criterion=$value: ${filteredFiles.size} files")

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

                withContext(Dispatchers.Main) {
                    when (criterion) {
                        "album" -> {
                            if (hasDiscMetadata) {
                                setupTabView(discNumbers, value, highlightSongUri, filteredFiles)
                            } else {
                                setupTabView(listOf("1"), value, highlightSongUri, filteredFiles)
                            }
                        }
                        "artist" -> {
                            setupArtistTabView(value, highlightSongUri, filteredFiles)
                        }
                        "genre" -> {
                            setupGenreTabView(value, highlightSongUri, filteredFiles)
                        }
                        "year" -> {
                            setupYearTabView(value, highlightSongUri, filteredFiles)
                        }
                        else -> {
                            val sortedFiles = filteredFiles.sortedBy { it.title ?: it.name }
                            setupListView(sortedFiles, highlightSongUri)
                        }
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
            Log.w(TAG, "No files available for $criterion=$value, hiding album artwork")
            withContext(Dispatchers.Main) {
                binding.albumArtwork.visibility = View.GONE
            }
            return
        }

        if (criterion == "artist") {
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
                    Glide.with(this@SongsActivity)
                        .load(imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(binding.albumArtwork)
                    binding.albumArtwork.visibility = View.VISIBLE

                    val imageBytes = withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient()
                            val request = Request.Builder().url(imageUrl).build()
                            val bytes = client.newCall(request).execute().body?.bytes()
                            if (bytes != null) {
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
                    Log.w(TAG, "No artist image found for $value, showing placeholder")
                    Glide.with(this@SongsActivity)
                        .load(android.R.drawable.ic_menu_gallery)
                        .into(binding.albumArtwork)
                    binding.albumArtwork.visibility = View.VISIBLE
                    binding.albumArtwork.setOnClickListener(null)
                }
            }
        } else if (criterion == "album") {
            val metadataExtractor = MetadataExtractor(this@SongsActivity)
            var artworkBytes: ByteArray? = null
            val allArtworks = mutableListOf<ByteArray>()
            var metadata: Metadata? = null

            for (file in files.take(3)) {
                Log.d(TAG, "Attempting to load artwork from file: ${file.uri}")
                try {
                    metadata = withContext(Dispatchers.IO) {
                        metadataExtractor.extractMetadata(file.uri)
                    }
                    if (metadata != null && metadata.artworkCount > 0) {
                        artworkBytes = withContext(Dispatchers.IO) {
                            metadataExtractor.getArtworkBytes(file.uri, 0)
                        }
                        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                            Log.d(TAG, "Found artwork for file: ${file.uri}, size: ${artworkBytes.size} bytes")
                            withContext(Dispatchers.IO) {
                                for (index in 0 until metadata.artworkCount) {
                                    val bytes = metadataExtractor.getArtworkBytes(file.uri, index)
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        allArtworks.add(bytes)
                                        Log.d(TAG, "Added artwork at index $index for file: ${file.uri}")
                                    }
                                }
                            }
                            break
                        }
                    } else {
                        Log.d(TAG, "No artwork found for file: ${file.uri}, artworkCount: ${metadata?.artworkCount ?: 0}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load metadata/artwork for ${file.uri}: ${e.message}", e)
                }
            }

            withContext(Dispatchers.Main) {
                if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                    Log.d(TAG, "Loading album artwork, size: ${artworkBytes.size} bytes")
                    Glide.with(this@SongsActivity)
                        .load(artworkBytes)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(binding.albumArtwork)
                    binding.albumArtwork.visibility = View.VISIBLE

                    binding.albumArtwork.setOnClickListener {
                        if (allArtworks.isNotEmpty()) {
                            Log.d(TAG, "Opening ImageViewerDialogFragment for album artwork, count: ${allArtworks.size}")
                            ImageViewerDialogFragment.newInstance(ArrayList(allArtworks), 0)
                                .show(supportFragmentManager, "ImageViewerDialog")
                        } else {
                            Log.w(TAG, "No artworks available for click")
                        }
                    }
                } else {
                    Log.w(TAG, "No album artwork found for any files, showing placeholder")
                    Glide.with(this@SongsActivity)
                        .load(android.R.drawable.ic_menu_gallery)
                        .into(binding.albumArtwork)
                    binding.albumArtwork.visibility = View.VISIBLE
                    binding.albumArtwork.setOnClickListener(null)
                }
            }
        } else {
            Log.d(TAG, "No image loading for criterion $criterion, hiding album artwork")
            withContext(Dispatchers.Main) {
                binding.albumArtwork.visibility = View.GONE
            }
        }
    }

    private fun setupTabView(discNumbers: List<String>, albumName: String, highlightSongUri: String?, files: List<MusicFile>) {
        binding.viewPager.offscreenPageLimit = discNumbers.size
        binding.viewPager.adapter = DiscPagerAdapter(this, discNumbers, albumName, highlightSongUri)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = "CD ${position + 1}"
        }.attach()
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.songsRecyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        binding.albumArtwork.visibility = if (binding.albumArtwork.drawable != null) View.VISIBLE else View.GONE

        for (i in 0 until binding.tabLayout.tabCount) {
            val tab = binding.tabLayout.getTabAt(i)
            tab?.view?.setOnLongClickListener {
                showTabContextMenu(it, i, discNumbers, albumName, files)
                true
            }
        }

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

    private fun showTabContextMenu(view: View, tabPosition: Int, discNumbers: List<String>, albumName: String, files: List<MusicFile>) {
        val popup = PopupMenu(this, view)
        menuInflater.inflate(R.menu.menu_album_tab, popup.menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.add_disc_to_playlist -> {
                    val discNumber = discNumbers[tabPosition]
                    val discFiles = files.filter { file ->
                        val fileDiscNumber = file.discNumber?.trim()?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()?.toString()
                        fileDiscNumber == discNumber
                    }.sortedBy { it.title ?: it.name }
                    Log.d(TAG, "Adding ${discFiles.size} songs from disc $discNumber to playlist")
                    if (discFiles.isNotEmpty()) {
                        AddMultipleToPlaylistDialogFragment.newInstance(discFiles)
                            .show(supportFragmentManager, "AddMultipleToPlaylistDialog")
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "Keine Lieder für diese CD gefunden",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
                R.id.add_album_to_playlist -> {
                    val albumFiles = files.sortedBy { it.title ?: it.name }
                    Log.d(TAG, "Adding ${albumFiles.size} songs from album $albumName to playlist")
                    if (albumFiles.isNotEmpty()) {
                        AddMultipleToPlaylistDialogFragment.newInstance(albumFiles)
                            .show(supportFragmentManager, "AddMultipleToPlaylistDialog")
                    } else {
                        android.widget.Toast.makeText(
                            this,
                            "Keine Lieder für dieses Album gefunden",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupArtistTabView(artistName: String, highlightSongUri: String?, files: List<MusicFile>) {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = ArtistPagerAdapter(this, artistName, highlightSongUri)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Titel"
                1 -> "Alben"
                else -> ""
            }
        }.attach()
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.songsRecyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        binding.albumArtwork.visibility = if (binding.albumArtwork.drawable != null) View.VISIBLE else View.GONE
    }

    private fun setupGenreTabView(genre: String, highlightSongUri: String?, files: List<MusicFile>) {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = GenrePagerAdapter(this, genre, highlightSongUri)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Titel"
                1 -> "Künstler"
                else -> ""
            }
        }.attach()
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.songsRecyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        binding.albumArtwork.visibility = if (binding.albumArtwork.drawable != null) View.VISIBLE else View.GONE
    }

    private fun setupYearTabView(year: String, highlightSongUri: String?, files: List<MusicFile>) {
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = YearPagerAdapter(this, year, highlightSongUri)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Titel"
                1 -> "Alben"
                else -> ""
            }
        }.attach()
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        binding.songsRecyclerView.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        binding.albumArtwork.visibility = View.GONE
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
        binding.albumArtwork.visibility = if (binding.albumArtwork.drawable != null) View.VISIBLE else View.GONE

        if (highlightSongUri != null) {
            val highlightUri = Uri.parse(highlightSongUri)
            val position = filteredFiles.indexOfFirst { it.uri == highlightUri }
            Log.d(TAG, "Attempting to highlight song in list view with URI: $highlightSongUri, position: $position")
            if (position >= 0) {
                binding.songsRecyclerView.layoutManager?.scrollToPosition(position)
                lifecycleScope.launch(Dispatchers.Main) {
                    delay(100)
                    adapter?.highlightItem(binding.songsRecyclerView, position)
                    Log.d(TAG, "Highlighted song in list view at position $position for URI: $highlightSongUri")
                }
            } else {
                Log.w(TAG, "Highlight song not found in filtered files for list view: $highlightSongUri")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
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

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}