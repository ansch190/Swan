package com.schwanitz.swan.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.schwanitz.swan.R
import com.schwanitz.swan.ui.fragment.ImageViewerDialogFragment

class ImageViewerAdapter(
    private val context: Context,
    private val artworks: List<ByteArray>
) : RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_viewer, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val artworkBytes = artworks[position]
        if (artworkBytes.isNotEmpty()) {
            Glide.with(context)
                .load(artworkBytes)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(android.R.drawable.ic_menu_close_clear_cancel)
                .into(holder.imageView)
            holder.imageView.visibility = View.VISIBLE

            // Klick-Listener zum Schließen des Dialogs
            holder.imageView.setOnClickListener {
                val activity = context as? FragmentActivity
                activity?.supportFragmentManager?.fragments?.forEach { fragment ->
                    if (fragment is ImageViewerDialogFragment) {
                        fragment.dismiss()
                    }
                }
            }
        } else {
            holder.imageView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = artworks.size
}