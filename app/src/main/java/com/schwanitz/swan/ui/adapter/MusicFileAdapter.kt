package com.schwanitz.swan.ui.adapter

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.ui.fragment.AddToPlaylistDialogFragment
import com.schwanitz.swan.ui.fragment.MetadataFragment

class MusicFileAdapter(
    private var musicFiles: List<MusicFile>,
    private val onItemClick: (Uri) -> Unit
) : RecyclerView.Adapter<MusicFileAdapter.MusicViewHolder>() {

    var filteredFiles: List<MusicFile> = musicFiles
        private set

    class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.music_title)

        fun bind(musicFile: MusicFile, onItemClick: (Uri) -> Unit, onLongClick: (MusicFile) -> Boolean) {
            title.text = musicFile.title?.takeIf { it.isNotBlank() } ?: musicFile.name
            itemView.setOnClickListener {
                Log.d("MusicFileAdapter", "Item clicked: ${musicFile.name}")
                onItemClick(musicFile.uri)
            }
            itemView.setOnLongClickListener {
                Log.d("MusicFileAdapter", "Long click detected on item: ${musicFile.name}")
                onLongClick(musicFile)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_file, parent, false)
        Log.d("MusicFileAdapter", "Creating ViewHolder for item_music_file")
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val musicFile = filteredFiles[position]
        Log.d("MusicFileAdapter", "Binding item at position $position: ${musicFile.name}")
        holder.bind(musicFile, onItemClick) { selectedMusicFile ->
            Log.d("MusicFileAdapter", "Triggering showPopupMenu for: ${selectedMusicFile.name}")
            showPopupMenu(holder.itemView, selectedMusicFile)
            true // Langes Klicken als behandelt markieren
        }
    }

    override fun getItemCount(): Int {
        Log.d("MusicFileAdapter", "Item count: ${filteredFiles.size}")
        return filteredFiles.size
    }

    fun updateFiles(newFiles: List<MusicFile>) {
        Log.d("MusicFileAdapter", "Updating files, new count: ${newFiles.size}")
        musicFiles = newFiles
        filteredFiles = newFiles
        notifyDataSetChanged()
    }

    fun highlightItem(recyclerView: RecyclerView, position: Int) {
        if (position < 0 || position >= filteredFiles.size) {
            Log.w("MusicFileAdapter", "Invalid position for highlight: $position, file count: ${filteredFiles.size}")
            return
        }
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position) as? MusicViewHolder
        if (viewHolder == null) {
            Log.w("MusicFileAdapter", "ViewHolder not found for position: $position")
            return
        }
        val view = viewHolder.itemView
        val context = view.context

        // Animierte Hervorhebung mit Türkis für grauen Hintergrund
        val colorFrom = ContextCompat.getColor(context, android.R.color.transparent)
        val colorTo = ContextCompat.getColor(context, R.color.highlight_songs) // Türkis
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
        animator.duration = 1000 // 1 Sekunde für den Hinweg
        animator.repeatCount = 1
        animator.repeatMode = ValueAnimator.REVERSE
        animator.addUpdateListener { animation ->
            view.setBackgroundColor(animation.animatedValue as Int)
        }
        animator.start()
        Log.d("MusicFileAdapter", "Highlighted item at position $position: ${filteredFiles[position].name}")
    }

    private fun showPopupMenu(view: View, musicFile: MusicFile) {
        Log.d("MusicFileAdapter", "Showing PopupMenu for: ${musicFile.name}")
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.context_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            Log.d("MusicFileAdapter", "Menu item selected: ${menuItem.itemId}, title: ${menuItem.title}")
            when (menuItem.itemId) {
                R.id.context_info -> {
                    Log.d("MusicFileAdapter", "Opening MetadataFragment for: ${musicFile.name}")
                    val activity = view.context as? AppCompatActivity
                    activity?.supportFragmentManager?.let { fragmentManager ->
                        MetadataFragment.newInstance(listOf(musicFile), 0)
                            .show(fragmentManager, "MetadataFragment")
                        Log.d("MusicFileAdapter", "MetadataFragment shown successfully")
                    } ?: Log.e("MusicFileAdapter", "Failed to get supportFragmentManager, context is not AppCompatActivity")
                    true
                }
                R.id.context_add_to_playlist -> {
                    Log.d("MusicFileAdapter", "Attempting to open AddToPlaylistDialogFragment for: ${musicFile.name}")
                    val activity = view.context as? AppCompatActivity
                    activity?.supportFragmentManager?.let { fragmentManager ->
                        try {
                            AddToPlaylistDialogFragment.newInstance(musicFile)
                                .show(fragmentManager, "AddToPlaylistDialog")
                            Log.d("MusicFileAdapter", "AddToPlaylistDialogFragment shown successfully")
                        } catch (e: Exception) {
                            Log.e("MusicFileAdapter", "Failed to show AddToPlaylistDialogFragment: ${e.message}", e)
                        }
                    } ?: Log.e("MusicFileAdapter", "Failed to get supportFragmentManager, context is not AppCompatActivity")
                    true
                }
                else -> {
                    Log.w("MusicFileAdapter", "Unknown menu item selected: ${menuItem.itemId}")
                    false
                }
            }
        }
        try {
            popup.show()
            Log.d("MusicFileAdapter", "PopupMenu shown successfully")
        } catch (e: Exception) {
            Log.e("MusicFileAdapter", "Failed to show PopupMenu: ${e.message}", e)
        }
    }
}