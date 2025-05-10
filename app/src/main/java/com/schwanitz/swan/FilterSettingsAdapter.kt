package com.schwanitz.swan

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class FilterSettingsAdapter(
    private val context: Context,
    private val onRemoveClick: (String) -> Unit
) : RecyclerView.Adapter<FilterSettingsAdapter.FilterViewHolder>() {

    private val filters = mutableListOf<FilterEntity>()

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filterText: TextView = itemView.findViewById(R.id.filterText)
        val removeButton: Button = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter, parent, false)
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

    fun setData(newFilters: List<FilterEntity>) {
        val diffCallback = FilterDiffCallback(filters, newFilters)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        filters.clear()
        filters.addAll(newFilters)
        diffResult.dispatchUpdatesTo(this)
    }

    private class FilterDiffCallback(
        private val oldList: List<FilterEntity>,
        private val newList: List<FilterEntity>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].criterion == newList[newItemPosition].criterion
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}