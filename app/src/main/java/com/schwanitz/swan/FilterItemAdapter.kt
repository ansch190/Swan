package com.schwanitz.swan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FilterItemAdapter(
    private var items: List<String>,
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: (String, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<FilterItemAdapter.ItemViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemText: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(item: String, position: Int, onItemClick: (String) -> Unit, onItemLongClick: (String, Int) -> Unit) {
            itemText.text = item
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
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position, onItemClick, onItemLongClick)
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