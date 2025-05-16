package com.schwanitz.swan

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ArtistAlbumsAdapter(
    private var albums: List<String>,
    private val onItemClick: (String) -> Unit,
    private val metadataExtractor: MetadataExtractor,
    private val artistName: String
) : RecyclerView.Adapter<ArtistAlbumsAdapter.AlbumViewHolder>() {

    private var files: List<MusicFile> = emptyList()
    private val TAG = "ArtistAlbumsAdapter"

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumName: TextView = itemView.findViewById(R.id.album_name)
        val albumArtwork: ImageView = itemView.findViewById(R.id.album_artwork)

        fun bind(album: String, files: List<MusicFile>, metadataExtractor: MetadataExtractor, artistName: String, onItemClick: (String) -> Unit) {
            albumName.text = album
            // Lade Albumcover für das erste Lied des Albums, das dem Albumkünstler entspricht
            val firstFile = files.firstOrNull {
                it.album?.equals(album, ignoreCase = true) == true &&
                        it.albumArtist?.equals(artistName, ignoreCase = true) == true
            }
            if (firstFile != null) {
                val artworkBytes = metadataExtractor.getArtworkBytes(firstFile.uri, 0)
                if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(artworkBytes)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(albumArtwork)
                    albumArtwork.visibility = View.VISIBLE
                } else {
                    albumArtwork.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    albumArtwork.visibility = View.VISIBLE
                }
            } else {
                albumArtwork.visibility = View.GONE
            }
            itemView.setOnClickListener { onItemClick(album) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artist_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        holder.bind(album, files, metadataExtractor, artistName, onItemClick)
    }

    override fun getItemCount(): Int = albums.size

    fun updateAlbums(newAlbums: List<String>, newFiles: List<MusicFile>) {
        albums = newAlbums
        files = newFiles
        notifyDataSetChanged()
    }
}