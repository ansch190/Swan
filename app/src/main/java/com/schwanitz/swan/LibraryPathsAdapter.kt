package com.schwanitz.swan

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LibraryPathsAdapter(
    private val paths: MutableList<LibraryPathEntity>,
    private val onRemoveClick: (String) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<LibraryPathsAdapter.PathViewHolder>() {

    inner class PathViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pathText: TextView = itemView.findViewById(R.id.pathText)
        val removeButton: Button = itemView.findViewById(R.id.removeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_path, parent, false)
        return PathViewHolder(view)
    }

    override fun onBindViewHolder(holder: PathViewHolder, position: Int) {
        val path = paths[position]
        holder.pathText.text = getReadablePath(path.uri)
        holder.removeButton.setOnClickListener {
            onRemoveClick(path.uri)
        }
    }

    override fun getItemCount(): Int = paths.size

    private fun getReadablePath(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val path = uri.path?.substringAfterLast("primary:") ?: uriString
            URLDecoder.decode(path, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            uriString // Fallback auf Roh-URI bei Fehler
        }
    }
}