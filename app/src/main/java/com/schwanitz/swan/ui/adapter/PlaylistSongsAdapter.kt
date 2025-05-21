package com.schwanitz.swan.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.entity.PlaylistSongEntity
import com.schwanitz.swan.domain.model.MusicFile
import java.util.Collections
import java.util.UUID

class PlaylistSongsAdapter(
    private var musicFiles: List<MusicFile>,
    private val onItemClick: (Uri) -> Unit
) : RecyclerView.Adapter<PlaylistSongsAdapter.SongViewHolder>() {

    private var isEditMode = false
    private var originalSongEntities: List<PlaylistSongEntity> = emptyList()
    private var songEntities: MutableList<PlaylistSongEntity> = mutableListOf()
    private var hasModifications = false

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
                holder.itemView.findViewTreeRecyclerView()?.let { recyclerView ->
                    recyclerView.findViewHolderForAdapterPosition(holder.bindingAdapterPosition)?.let {
                        val itemTouchHelper = ItemTouchHelper::class.java.getDeclaredField("mCallback")
                            .apply { isAccessible = true }
                            .get(recyclerView.getTag(R.id.item_touch_helper_previous_state))

                        ItemTouchHelper::class.java.getDeclaredField("mRecyclerView")
                            .apply { isAccessible = true }
                            .get(recyclerView.getTag(R.id.item_touch_helper_previous_state))
                            ?.let { mRecyclerView ->
                                ItemTouchHelper::class.java.getDeclaredMethod(
                                    "select",
                                    RecyclerView.ViewHolder::class.java,
                                    Int::class.java
                                ).apply { isAccessible = true }
                                    .invoke(
                                        recyclerView.getTag(R.id.item_touch_helper_previous_state),
                                        it,
                                        ItemTouchHelper.ACTION_STATE_DRAG
                                    )
                            }
                    }
                }
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

// Hilfsmethode, um das RecyclerView aus einer View zu finden
private fun View.findViewTreeRecyclerView(): RecyclerView? {
    var parent = this.parent
    while (parent != null) {
        if (parent is RecyclerView) {
            return parent
        }
        parent = parent.parent
    }
    return null
}