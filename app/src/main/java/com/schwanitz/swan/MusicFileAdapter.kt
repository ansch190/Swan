package com.schwanitz.swan

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity  // Erforderliche Importanweisung
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

class MusicFileAdapter(
    private var musicFiles: List<MusicFile>,
    private val onItemClick: (Uri) -> Unit
) : RecyclerView.Adapter<MusicFileAdapter.MusicViewHolder>() {

    var filteredFiles: List<MusicFile> = musicFiles
        private set

    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(musicFile: MusicFile, onItemClick: (Uri) -> Unit, onLongClick: (MusicFile) -> Unit) {
            title.text = musicFile.title?.takeIf { it.isNotBlank() } ?: musicFile.name
            itemView.setOnClickListener { onItemClick(musicFile.uri) }
            itemView.setOnLongClickListener {
                onLongClick(musicFile)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val musicFile = filteredFiles[position]
        holder.bind(musicFile, onItemClick) { selectedMusicFile ->
            showPopupMenu(holder.itemView, selectedMusicFile)
        }
    }

    override fun getItemCount(): Int = filteredFiles.size

    fun updateFiles(newFiles: List<MusicFile>) {
        musicFiles = newFiles
        filteredFiles = newFiles
        notifyDataSetChanged()
    }

    private fun showPopupMenu(view: View, musicFile: MusicFile) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.context_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.context_info -> {
                    val fragmentManager = (view.context as? AppCompatActivity)?.supportFragmentManager
                    fragmentManager?.let {
                        MetadataFragment.newInstance(listOf(musicFile), 0)
                            .show(it, "MetadataFragment")
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}