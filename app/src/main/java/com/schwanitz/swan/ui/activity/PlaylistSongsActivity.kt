package com.schwanitz.swan.ui.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.MusicFileEntity
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.databinding.ActivityPlaylistSongsBinding
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.service.MusicPlaybackService
import com.schwanitz.swan.ui.adapter.MusicFileAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaylistSongsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"
    }

    private lateinit var binding: ActivityPlaylistSongsBinding
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "PlaylistSongsActivity"

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
        binding = ActivityPlaylistSongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setze Toolbar und Titel
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
        supportActionBar?.title = playlistName

        // Initialisiere RecyclerView
        val adapter = MusicFileAdapter(emptyList()) { uri ->
            musicService?.play(uri)
        }
        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaylistSongsActivity)
            this.adapter = adapter
        }

        // Lade Songs der Playlist
        val playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
        if (playlistId != null) {
            lifecycleScope.launch {
                val songs = AppDatabase.getDatabase(this@PlaylistSongsActivity)
                    .playlistDao()
                    .getSongsForPlaylist(playlistId)
                val musicFiles: List<MusicFile> = songs.mapNotNull { song: PlaylistSongEntity ->
                    val musicFileEntity = AppDatabase.getDatabase(this@PlaylistSongsActivity)
                        .musicFileDao()
                        .getFileByUri(song.songUri)
                        .first() // Konvertiere Flow zu einem einzelnen Wert
                    musicFileEntity?.let { entity: MusicFileEntity ->
                        MusicFile(
                            uri = Uri.parse(entity.uri),
                            name = entity.name,
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album,
                            albumArtist = entity.albumArtist,
                            discNumber = entity.discNumber,
                            trackNumber = entity.trackNumber,
                            year = entity.year,
                            genre = entity.genre,
                            fileSize = entity.fileSize,
                            audioCodec = entity.audioCodec,
                            sampleRate = entity.sampleRate,
                            bitrate = entity.bitrate,
                            tagVersion = entity.tagVersion
                        )
                    }
                }
                adapter.updateFiles(musicFiles)
                binding.emptyText.visibility = if (musicFiles.isEmpty()) View.VISIBLE else View.GONE
            }
        } else {
            Log.w(TAG, "No playlistId provided")
            binding.emptyText.text = getString(R.string.no_playlist_selected)
            binding.emptyText.visibility = View.VISIBLE
        }

        // Binde MusicPlaybackService
        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
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
        Log.d(TAG, "Activity destroyed")
    }
}