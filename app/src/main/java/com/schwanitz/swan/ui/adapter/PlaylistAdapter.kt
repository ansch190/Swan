package com.schwanitz.swan.ui.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.ui.activity.PlaylistSongsActivity

class PlaylistAdapter(
    private var playlists: List<PlaylistEntity>,
    private val onItemLongClick: (PlaylistEntity, View) -> Unit // View hinzugefügt
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    var selectedPosition: Int = RecyclerView.NO_POSITION
        private set

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()
    }

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.playlistName)

        fun bind(playlist: PlaylistEntity, position: Int) {
            nameText.text = playlist.name
            itemView.isSelected = position == selectedPosition

            // Klick-Listener
            itemView.setOnClickListener {
                selectedPosition = position
                notifyDataSetChanged()
                // Starte PlaylistSongsActivity
                val context = itemView.context
                val intent = Intent(context, PlaylistSongsActivity::class.java).apply {
                    putExtra(PlaylistSongsActivity.EXTRA_PLAYLIST_ID, playlist.id)
                    putExtra(PlaylistSongsActivity.EXTRA_PLAYLIST_NAME, playlist.name)
                }
                context.startActivity(intent)
            }

            // Langer-Klick-Listener
            itemView.setOnLongClickListener {
                setSelectedPosition(position)
                onItemLongClick(playlist, itemView) // Übergebe die PlaylistEntity und die View
                true // Wir geben true zurück, um zu zeigen, dass der lange Klick behandelt wurde
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlists[position], position)
    }

    override fun getItemCount(): Int = playlists.size

    fun updatePlaylists(newPlaylists: List<PlaylistEntity>) {
        playlists = newPlaylists
        notifyDataSetChanged()
    }

    fun getPlaylistAt(position: Int): PlaylistEntity? {
        return playlists.getOrNull(position)
    }
}