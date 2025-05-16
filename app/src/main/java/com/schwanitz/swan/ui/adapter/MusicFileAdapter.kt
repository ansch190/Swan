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
            .inflate(R.layout.item_music_file, parent, false)
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
                R.id.context_add_to_playlist -> {
                    val fragmentManager = (view.context as? AppCompatActivity)?.supportFragmentManager
                    fragmentManager?.let {
                        AddToPlaylistDialogFragment.newInstance(musicFile)
                            .show(it, "AddToPlaylistDialog")
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}