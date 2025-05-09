package com.schwanitz.swan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FilterSettingsAdapter(
    private val filters: MutableList<FilterEntity>,
    private val onRemoveClick: (String) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<FilterSettingsAdapter.FilterViewHolder>() {

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filterText: TextView = itemView.findViewById(R.id.pathText)
        val removeButton: Button = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_path, parent, false)
        return FilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        holder.filterText.text = filter.displayName
        holder.removeButton.setOnClickListener {
            onRemoveClick(filter.criterion)
        }
    }

    override fun getItemCount(): Int = filters.size
}