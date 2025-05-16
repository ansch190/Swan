package com.schwanitz.swan.ui.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.entity.LibraryPathEntity
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LibraryPathsAdapter(
    private val paths: MutableList<LibraryPathEntity>,
    private val onActionClick: (String, Boolean) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<LibraryPathsAdapter.PathViewHolder>() {

    private var scanningPathUri: String? = null
    private val TAG = "LibraryPathsAdapter"

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
        val isScanning = path.uri == scanningPathUri
        holder.removeButton.text = context.getString(
            if (isScanning) R.string.cancel_scan else R.string.remove_path
        )
        holder.removeButton.contentDescription = context.getString(
            if (isScanning) R.string.cancel_scan_description else R.string.remove_path_description
        )
        holder.removeButton.isEnabled = true // Sicherstellen, dass der Button aktiviert ist
        holder.removeButton.setOnClickListener {
            Log.d(TAG, "Button clicked for uri: ${path.uri}, isCancel: $isScanning")
            onActionClick(path.uri, isScanning)
        }
    }

    override fun getItemCount(): Int = paths.size

    fun setScanningPath(uri: String?) {
        Log.d(TAG, "Setting scanning path: $uri")
        scanningPathUri = uri
        notifyDataSetChanged()
    }

    private fun getReadablePath(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val path = uri.path?.substringAfterLast("primary:") ?: uriString
            URLDecoder.decode(path, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            uriString
        }
    }
}