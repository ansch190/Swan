package com.schwanitz.swan.ui.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.MusicFileEntity
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.databinding.ActivityPlaylistSongsBinding
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.service.MusicPlaybackService
import com.schwanitz.swan.ui.adapter.PlaylistSongsAdapter
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections

class PlaylistSongsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"
    }

    private lateinit var binding: ActivityPlaylistSongsBinding
    private lateinit var viewModel: MainViewModel
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "PlaylistSongsActivity"
    private var currentPlaylistId: String? = null
    private var isEditMode = false
    private lateinit var adapter: PlaylistSongsAdapter

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

    // ItemTouchHelper für Drag & Drop
    private val itemTouchHelper by lazy {
        val simpleCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition

                // Position in der Liste aktualisieren
                adapter.swapItems(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Nicht verwendet, da wir nur vertikales Verschieben implementieren
            }

            override fun isLongPressDragEnabled(): Boolean {
                // Nur im Bearbeitungsmodus aktivieren
                return isEditMode
            }
        }
        ItemTouchHelper(simpleCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistSongsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ViewModel initialisieren
        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(this, MusicRepository(this))
        ).get(MainViewModel::class.java)

        // Toolbar einrichten
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
        supportActionBar?.title = playlistName

        // Playlist-ID aus Intent laden
        currentPlaylistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)

        // Adapter initialisieren
        adapter = PlaylistSongsAdapter(emptyList()) { uri ->
            // Nur abspielen, wenn nicht im Bearbeitungsmodus
            if (!isEditMode) {
                musicService?.play(uri)
            }
        }

        binding.songsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaylistSongsActivity)
            adapter = this@PlaylistSongsActivity.adapter
        }

        // ItemTouchHelper an das RecyclerView anhängen
        itemTouchHelper.attachToRecyclerView(binding.songsRecyclerView)

        // Songs laden
        loadPlaylistSongs()

        // ServiceConnection starten
        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun loadPlaylistSongs() {
        val playlistId = currentPlaylistId ?: return

        lifecycleScope.launch {
            val songs = AppDatabase.getDatabase(this@PlaylistSongsActivity)
                .playlistDao()
                .getSongsForPlaylist(playlistId)
                .sortedBy { it.position }

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

            adapter.setSongs(musicFiles, songs)
            binding.emptyText.visibility = if (musicFiles.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_playlist_songs, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Speichere Änderungen und kehre zurück
                if (isEditMode) {
                    saveChanges()
                }
                finish()
                true
            }
            R.id.action_edit -> {
                // Editiermodus umschalten
                isEditMode = !isEditMode

                // UI aktualisieren
                item.setIcon(if (isEditMode) R.drawable.ic_check else R.drawable.ic_edit)
                item.setTitle(if (isEditMode) R.string.save_changes else R.string.edit_playlist)

                // Drag-Handle-Sichtbarkeit umschalten
                adapter.setEditMode(isEditMode)

                // Wenn der Bearbeitungsmodus beendet wird, Änderungen speichern
                if (!isEditMode) {
                    saveChanges()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveChanges() {
        val playlistId = currentPlaylistId ?: return

        // Nur speichern, wenn Änderungen vorgenommen wurden
        if (adapter.hasChanges()) {
            lifecycleScope.launch {
                val updatedSongs = adapter.getPlaylistSongs(playlistId)
                viewModel.updatePlaylistSongOrder(playlistId, updatedSongs)
            }
        }
    }

    override fun onBackPressed() {
        // Speichere Änderungen beim Zurück-Drücken
        if (isEditMode) {
            saveChanges()
            isEditMode = false
            adapter.setEditMode(false)
            supportActionBar?.title = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
        } else {
            super.onBackPressed()
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