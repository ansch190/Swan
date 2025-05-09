package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.ActivitySongsBinding

class SongsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySongsBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: MusicFileAdapter
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "SongsActivity"

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
        binding = ActivitySongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this, MainViewModelFactory(this, MusicRepository(this))).get(MainViewModel::class.java)

        val criterion = intent.getStringExtra("criterion") ?: "title"
        val value = intent.getStringExtra("value") ?: ""
        supportActionBar?.title = "$criterion: $value"

        adapter = MusicFileAdapter(
            musicFiles = emptyList(),
            onItemClick = { uri ->
                musicService?.play(uri)
            },
            onShowMetadata = { musicFile ->
                Log.d(TAG, "Showing metadata for file: ${musicFile.name}")
                val position = adapter.filteredFiles.indexOf(musicFile)
                if (position >= 0) {
                    MetadataFragment.newInstance(adapter.filteredFiles, position)
                        .show(supportFragmentManager, "MetadataFragment")
                }
            }
        )
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SongsActivity)
            this.adapter = adapter
        }

        viewModel.musicFiles.observe(this) { files ->
            val filteredFiles = files.filter { file ->
                when (criterion) {
                    "title" -> (file.title ?: file.name) == value
                    "artist" -> file.artist == value
                    "album" -> file.album == value
                    "albumArtist" -> file.albumArtist == value
                    "discNumber" -> file.discNumber?.toString() == value
                    "trackNumber" -> file.trackNumber?.toString() == value
                    "year" -> file.year?.toString() == value
                    "genre" -> file.genre == value
                    else -> false
                }
            }
            Log.d(TAG, "Loaded songs for $criterion=$value: ${filteredFiles.size}")
            adapter.updateFiles(filteredFiles)
        }

        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
}