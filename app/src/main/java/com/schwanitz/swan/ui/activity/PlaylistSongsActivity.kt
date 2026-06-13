package com.schwanitz.swan.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.schwanitz.swan.util.Logger
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import dagger.hilt.android.AndroidEntryPoint
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.databinding.ActivityPlaylistSongsBinding
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.repository.MusicRepository
import com.schwanitz.swan.ui.adapter.PlaylistSongsAdapter
import javax.inject.Inject
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Collections

@AndroidEntryPoint
class PlaylistSongsActivity : BaseMusicActivity() {

    companion object {
        const val EXTRA_PLAYLIST_ID = "extra_playlist_id"
        const val EXTRA_PLAYLIST_NAME = "extra_playlist_name"
    }

    private lateinit var binding: ActivityPlaylistSongsBinding
    private lateinit var viewModel: MainViewModel
    @Inject lateinit var repository: MusicRepository
    private val TAG = "PlaylistSongsActivity"
    private var currentPlaylistId: String? = null
    private var isEditMode = false
    private lateinit var adapter: PlaylistSongsAdapter

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
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Toolbar einrichten
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
        supportActionBar?.title = playlistName

        onBackPressedDispatcher.addCallback(this) {
            if (isEditMode) {
                saveChanges()
                isEditMode = false
                adapter.setEditMode(false)
                supportActionBar?.title = playlistName
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        // Playlist-ID aus Intent laden
        currentPlaylistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)

        // Adapter initialisieren
        adapter = PlaylistSongsAdapter(emptyList(), onItemClick = { uri ->
            // Nur abspielen, wenn nicht im Bearbeitungsmodus
            if (!isEditMode) {
                musicService?.play(uri)
            }
        }) { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
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
        bindMusicService()
    }

    private fun loadPlaylistSongs() {
        val playlistId = currentPlaylistId ?: return

        lifecycleScope.launch {
            val songs = repository.getSongsForPlaylist(playlistId)
                .sortedBy { it.position }

            val musicFiles: List<MusicFile> = songs.mapNotNull { song ->
                repository.getFileByUri(song.songUri)
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
}