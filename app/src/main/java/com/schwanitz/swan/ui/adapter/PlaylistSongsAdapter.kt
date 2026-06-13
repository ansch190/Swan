package com.schwanitz.swan.ui.adapter

import android.net.Uri
import com.schwanitz.swan.util.Logger
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.ui.fragment.AddToPlaylistDialogFragment
import com.schwanitz.swan.ui.fragment.MetadataFragment
import java.util.Collections

class PlaylistSongsAdapter(
    private var musicFiles: List<MusicFile>,
    private val onItemClick: (Uri) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : RecyclerView.Adapter<PlaylistSongsAdapter.SongViewHolder>() {

    private var isEditMode = false
    private var originalSongEntities: List<PlaylistSongEntity> = emptyList()
    private var songEntities: MutableList<PlaylistSongEntity> = mutableListOf()
    private var hasModifications = false
    private val TAG = "PlaylistSongsAdapter"

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.song_title)
        val artist: TextView = itemView.findViewById(R.id.song_artist)
        val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val musicFile = musicFiles[position]

        holder.title.text = musicFile.title ?: musicFile.name
        holder.artist.text = musicFile.artist ?: "Unbekannter Künstler"

        // Drag-Handle nur im Bearbeitungsmodus anzeigen
        holder.dragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE

        // Im Bearbeitungsmodus Drag & Drop konfigurieren
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && isEditMode) {
                onStartDrag(holder)
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }

        // Klick-Listener für Wiedergabe
        holder.itemView.setOnClickListener {
            if (!isEditMode) {
                onItemClick(musicFile.uri)
            }
        }

        // Long-Click-Listener für Kontextmenü
        holder.itemView.setOnLongClickListener {
            if (!isEditMode) {
                showPopupMenu(holder.itemView, musicFile)
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }
    }

    // Methode zum Anzeigen des Popup-Menüs
    private fun showPopupMenu(view: View, musicFile: MusicFile) {
        Logger.d(TAG, "Showing PopupMenu for: ${musicFile.name}")
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.context_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            Logger.d(TAG, "Menu item selected: ${menuItem.itemId}, title: ${menuItem.title}")
            when (menuItem.itemId) {
                R.id.context_info -> {
                    Logger.d(TAG, "Opening MetadataFragment for: ${musicFile.name}")
                    val activity = view.context as? AppCompatActivity
                    activity?.supportFragmentManager?.let { fragmentManager ->
                        MetadataFragment.newInstance(listOf(musicFile), 0)
                            .show(fragmentManager, "MetadataFragment")
                        Logger.d(TAG, "MetadataFragment shown successfully")
                    } ?: Logger.e(TAG, "Failed to get supportFragmentManager, context is not AppCompatActivity")
                    true
                }
                R.id.context_add_to_playlist -> {
                    Logger.d(TAG, "Attempting to open AddToPlaylistDialogFragment for: ${musicFile.name}")
                    val activity = view.context as? AppCompatActivity
                    activity?.supportFragmentManager?.let { fragmentManager ->
                        try {
                            AddToPlaylistDialogFragment.newInstance(musicFile)
                                .show(fragmentManager, "AddToPlaylistDialog")
                            Logger.d(TAG, "AddToPlaylistDialogFragment shown successfully")
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to show AddToPlaylistDialogFragment: ${e.message}", e)
                        }
                    } ?: Logger.e(TAG, "Failed to get supportFragmentManager, context is not AppCompatActivity")
                    true
                }
                else -> {
                    Logger.w(TAG, "Unknown menu item selected: ${menuItem.itemId}")
                    false
                }
            }
        }
        try {
            popup.show()
            Logger.d(TAG, "PopupMenu shown successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to show PopupMenu: ${e.message}", e)
        }
    }

    override fun getItemCount(): Int = musicFiles.size

    // Auf Bearbeitungsmodus umschalten
    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        notifyDataSetChanged()
    }

    // Elemente in der Liste tauschen
    fun swapItems(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(musicFiles, i, i + 1)
                Collections.swap(songEntities, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(musicFiles, i, i - 1)
                Collections.swap(songEntities, i, i - 1)
            }
        }

        // Positionen aktualisieren
        for (i in songEntities.indices) {
            songEntities[i] = songEntities[i].copy(position = i)
        }

        notifyItemMoved(fromPosition, toPosition)
        hasModifications = true
    }

    // Songs setzen und PlaylistSongEntity speichern
    fun setSongs(newFiles: List<MusicFile>, entities: List<PlaylistSongEntity>) {
        musicFiles = newFiles
        originalSongEntities = entities
        songEntities = entities.toMutableList()
        hasModifications = false
        notifyDataSetChanged()
    }

    // Prüfen, ob Änderungen vorliegen
    fun hasChanges(): Boolean {
        return hasModifications
    }

    // Aktuelle PlaylistSongEntity-Liste abrufen
    fun getPlaylistSongs(playlistId: String): List<PlaylistSongEntity> {
        return songEntities
    }
}