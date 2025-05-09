package com.schwanitz.swan

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ArtworkAdapter(private val context: Context) : RecyclerView.Adapter<ArtworkAdapter.ArtworkViewHolder>() {

    private var displayedArtworks: List<ByteArray> = emptyList()
    private var allArtworks: List<ByteArray> = emptyList()
    private val TAG = "ArtworkAdapter"

    class ArtworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.artwork_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artwork, parent, false)
        return ArtworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtworkViewHolder, position: Int) {
        val artworkBytes = displayedArtworks[position]
        if (artworkBytes.isNotEmpty()) {
            Log.d(TAG, "Loading artwork at position $position, size: ${artworkBytes.size} bytes")
            Glide.with(context)
                .load(artworkBytes)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(android.R.drawable.ic_menu_close_clear_cancel)
                .into(holder.imageView)
            holder.imageView.visibility = View.VISIBLE

            // Klick-Listener zum Öffnen des ImageViewerDialogFragment
            holder.imageView.setOnClickListener {
                val activity = context as? FragmentActivity
                activity?.supportFragmentManager?.let { fragmentManager ->
                    ImageViewerDialogFragment.newInstance(ArrayList(allArtworks), position)
                        .show(fragmentManager, "ImageViewerDialog")
                }
            }
        } else {
            Log.d(TAG, "Empty artwork at position $position")
            holder.imageView.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = displayedArtworks.size

    fun setData(displayedArtworks: List<ByteArray>, allArtworks: List<ByteArray>) {
        this.displayedArtworks = displayedArtworks
        this.allArtworks = allArtworks
        Log.d(TAG, "Set data with ${displayedArtworks.size} displayed artworks, ${allArtworks.size} total artworks")
        notifyDataSetChanged()
    }
}