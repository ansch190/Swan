package com.schwanitz.swan.ui.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.schwanitz.swan.databinding.ItemFullScreenImageBinding
import com.schwanitz.swan.domain.usecase.MetadataExtractor

class FullScreenImageAdapter(
    private val context: Context,
    private val metadataExtractor: MetadataExtractor
) : RecyclerView.Adapter<FullScreenImageAdapter.ImageViewHolder>() {

    private var uri: Uri? = null
    private var artworkCount: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemFullScreenImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        uri?.let { uri ->
            val artworkBytes = metadataExtractor.getArtworkBytes(uri, position)
            if (artworkBytes != null) {
                Glide.with(context)
                    .load(artworkBytes)
                    .fitCenter()
                    .into(holder.binding.imageView)
            }
        }
    }

    override fun getItemCount(): Int = artworkCount

    fun setData(uri: Uri, count: Int) {
        this.uri = uri
        this.artworkCount = count
        notifyDataSetChanged()
    }

    class ImageViewHolder(val binding: ItemFullScreenImageBinding) : RecyclerView.ViewHolder(binding.root)
}