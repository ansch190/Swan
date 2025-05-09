package com.schwanitz.swan

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicFileAdapter(
    private var musicFiles: List<MusicFile>,
    private val onItemClick: (Uri) -> Unit,
    private val onShowMetadata: (MusicFile) -> Unit
) : RecyclerView.Adapter<MusicFileAdapter.MusicViewHolder>() {

    var filteredFiles: List<MusicFile> = musicFiles
        private set
    var selectedPosition: Int = RecyclerView.NO_POSITION

    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(musicFile: MusicFile, position: Int, onItemClick: (Uri) -> Unit, onLongClick: (Int) -> Unit) {
            title.text = musicFile.title?.takeIf { it.isNotBlank() } ?: musicFile.name
            itemView.setOnClickListener { onItemClick(musicFile.uri) }
            itemView.setOnLongClickListener {
                onLongClick(position)
                true
            }
            itemView.isLongClickable = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val musicFile = filteredFiles[position]
        holder.bind(musicFile, position, onItemClick) { pos ->
            selectedPosition = pos
            Log.d("MusicFileAdapter", "Long click at position=$pos, uri=${musicFile.uri}, name=${musicFile.name}")
            holder.itemView.showContextMenu()
        }
        Log.d("MusicFileAdapter", "Bound position=$position, uri=${musicFile.uri}, title=${musicFile.title ?: musicFile.name}")
    }

    override fun getItemCount(): Int = filteredFiles.size

    fun updateFiles(newFiles: List<MusicFile>) {
        musicFiles = newFiles
        filteredFiles = newFiles
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun filter(query: String?, criterion: String = "title") {
        filteredFiles = if (query.isNullOrBlank()) {
            musicFiles
        } else {
            musicFiles.filter { file ->
                when (criterion) {
                    "artist" -> file.artist?.contains(query, ignoreCase = true) ?: false
                    "album" -> file.album?.contains(query, ignoreCase = true) ?: false
                    "genre" -> file.genre?.contains(query, ignoreCase = true) ?: false
                    "title" -> (file.title?.contains(query, ignoreCase = true) ?: false) ||
                            file.name.contains(query, ignoreCase = true) ||
                            file.uri.lastPathSegment?.contains(query, ignoreCase = true) == true
                    else -> (file.title?.contains(query, ignoreCase = true) ?: false) ||
                            file.name.contains(query, ignoreCase = true) ||
                            file.uri.lastPathSegment?.contains(query, ignoreCase = true) == true
                }
            }
        }
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }
}