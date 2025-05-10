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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.databinding.ActivitySongsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        supportActionBar?.title = value // Nur der Album-Name als Titel

        adapter = MusicFileAdapter(
            musicFiles = emptyList(),
            onItemClick = { uri ->
                Log.d(TAG, "Playing file with URI: $uri")
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
            adapter = this@SongsActivity.adapter
            registerForContextMenu(this)
        }

        viewModel.musicFiles.observe(this) { files ->
            lifecycleScope.launch(Dispatchers.Default) {
                val filteredFiles = files.filter { file ->
                    when (criterion) {
                        "album" -> file.album?.equals(value, ignoreCase = true) ?: false
                        else -> false
                    }
                }.sortedBy { file ->
                    file.trackNumber?.toIntOrNull() ?: Int.MAX_VALUE
                }
                withContext(Dispatchers.Main) {
                    adapter.updateFiles(filteredFiles)
                    binding.songsRecyclerView.visibility = if (filteredFiles.isEmpty()) View.GONE else View.VISIBLE
                    binding.emptyText.visibility = if (filteredFiles.isEmpty()) View.VISIBLE else View.GONE
                }
            }
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
                val position = adapter.selectedPosition
                if (position != RecyclerView.NO_POSITION) {
                    val musicFile = adapter.filteredFiles[position]
                    MetadataFragment.newInstance(listOf(musicFile), 0)
                        .show(supportFragmentManager, "MetadataFragment")
                    true
                } else {
                    false
                }
            }
            else -> super.onContextItemSelected(item)
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
        unregisterForContextMenu(binding.songsRecyclerView)
    }
}