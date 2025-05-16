package com.schwanitz.swan.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.repository.ArtistImageRepository
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilterItemAdapter(
    private var items: List<String>,
    val onItemClick: (String) -> Unit, // Als öffentliche Eigenschaft
    val onItemLongClick: (String, Int) -> Unit = { _, _ -> }, // Als öffentliche Eigenschaft
    private val criterion: String?,
    private val artistImageRepository: ArtistImageRepository,
    private val metadataExtractor: MetadataExtractor,
    private val musicFiles: List<MusicFile>
) : RecyclerView.Adapter<FilterItemAdapter.ItemViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private val TAG = "FilterItemAdapter"
    private val imageLoadJobs = mutableMapOf<Int, Job>()

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemText: TextView = itemView.findViewById(R.id.filterText)
        val artworkImage: ImageView = itemView.findViewById(R.id.artworkImage)

        fun bind(
            item: String,
            position: Int,
            criterion: String?,
            artistImageRepository: ArtistImageRepository,
            metadataExtractor: MetadataExtractor,
            musicFiles: List<MusicFile>,
            onItemClick: (String) -> Unit,
            onItemLongClick: (String, Int) -> Unit,
            imageLoadJobs: MutableMap<Int, Job>,
            tag: String
        ) {
            itemText.text = item
            // Zurücksetzen des ImageView vor dem Laden eines neuen Bildes
            artworkImage.setImageDrawable(null)
            artworkImage.visibility = View.GONE

            // Entferne vorherigen Lade-Job für diese Position
            imageLoadJobs[position]?.cancel()
            imageLoadJobs.remove(position)

            // Zeige Bild basierend auf criterion
            when (criterion) {
                "artist" -> {
                    artworkImage.visibility = View.VISIBLE
                    val job = CoroutineScope(Dispatchers.Main).launch {
                        val imageUrl = withContext(Dispatchers.IO) {
                            try {
                                artistImageRepository.getArtistImageUrl(item)
                            } catch (e: Exception) {
                                Log.e(tag, "Error loading image for artist $item: ${e.message}")
                                null
                            }
                        }
                        if (imageUrl != null) {
                            Glide.with(itemView.context)
                                .load(imageUrl)
                                .apply(RequestOptions()
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .error(android.R.drawable.ic_menu_close_clear_cancel))
                                .into(artworkImage)
                            Log.d(tag, "Loaded image for artist: $item, URL: $imageUrl")
                        } else {
                            artworkImage.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                            Log.d(tag, "No image available for artist: $item")
                        }
                    }
                    imageLoadJobs[position] = job
                }
                "album" -> {
                    artworkImage.visibility = View.VISIBLE
                    val job = CoroutineScope(Dispatchers.Main).launch {
                        val artworkBytes = withContext(Dispatchers.IO) {
                            try {
                                // Finde das erste Lied des Albums
                                val firstFile = musicFiles.firstOrNull {
                                    it.album?.equals(item, ignoreCase = true) == true
                                }
                                firstFile?.let { metadataExtractor.getArtworkBytes(it.uri, 0) }
                            } catch (e: Exception) {
                                Log.e(tag, "Error loading artwork for album $item: ${e.message}")
                                null
                            }
                        }
                        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                            Glide.with(itemView.context)
                                .load(artworkBytes)
                                .apply(RequestOptions()
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .error(android.R.drawable.ic_menu_close_clear_cancel))
                                .into(artworkImage)
                            Log.d(tag, "Loaded artwork for album: $item, size: ${artworkBytes.size} bytes")
                        } else {
                            artworkImage.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                            Log.d(tag, "No artwork available for album: $item")
                        }
                    }
                    imageLoadJobs[position] = job
                }
            }

            itemView.setOnClickListener { onItemClick(item) }
            itemView.setOnLongClickListener {
                onItemLongClick(item, position)
                true
            }
            itemView.isLongClickable = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter_with_artwork, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position, criterion, artistImageRepository, metadataExtractor, musicFiles, onItemClick, onItemLongClick, imageLoadJobs, TAG)
    }

    override fun onViewRecycled(holder: ItemViewHolder) {
        super.onViewRecycled(holder)
        // Breche den Lade-Job ab, wenn der ViewHolder recycelt wird
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            imageLoadJobs[position]?.cancel()
            imageLoadJobs.remove(position)
        }
        // Setze das ImageView zurück
        holder.artworkImage.setImageDrawable(null)
        holder.artworkImage.visibility = View.GONE
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<String>) {
        items = newItems
        selectedPosition = RecyclerView.NO_POSITION
        // Breche alle laufenden Lade-Jobs ab
        imageLoadJobs.values.forEach { it.cancel() }
        imageLoadJobs.clear()
        notifyDataSetChanged()
    }

    fun getSelectedPosition(): Int = selectedPosition

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
    }

    fun getItemAt(position: Int): String? {
        return if (position in 0 until items.size) items[position] else null
    }
}