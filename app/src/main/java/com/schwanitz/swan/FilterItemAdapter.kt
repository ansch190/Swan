package com.schwanitz.swan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FilterItemAdapter(
    private var items: List<String>,
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: (String, Int) -> Unit = { _, _ -> },
    private val criterion: String?,
    private val artistImageRepository: ArtistImageRepository
) : RecyclerView.Adapter<FilterItemAdapter.ItemViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private val TAG = "FilterItemAdapter"

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemText: TextView = itemView.findViewById(R.id.filterText)
        val artistImage: ImageView = itemView.findViewById(R.id.artistImage)

        fun bind(
            item: String,
            position: Int,
            criterion: String?,
            artistImageRepository: ArtistImageRepository,
            onItemClick: (String) -> Unit,
            onItemLongClick: (String, Int) -> Unit
        ) {
            itemText.text = item
            // Zeige Künstlerbild nur für criterion == "artist"
            if (criterion == "artist") {
                artistImage.visibility = View.VISIBLE
                CoroutineScope(Dispatchers.Main).launch {
                    val imageUrl = withContext(Dispatchers.IO) {
                        try {
                            artistImageRepository.getArtistImageUrl(item)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (imageUrl != null) {
                        Glide.with(itemView.context)
                            .load(imageUrl)
                            .error(android.R.drawable.ic_menu_close_clear_cancel)
                            .into(artistImage)
                    } else {
                        artistImage.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    }
                }
            } else {
                artistImage.visibility = View.GONE
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
            .inflate(R.layout.item_filter, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position, criterion, artistImageRepository, onItemClick, onItemLongClick)
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<String>) {
        items = newItems
        selectedPosition = RecyclerView.NO_POSITION
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