package com.schwanitz.swan.ui.adapter

import android.content.ContentValues.TAG
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.schwanitz.swan.R
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.usecase.MetadataExtractor

class ArtistAlbumsAdapter(
    private var albums: List<String>,
    private val onItemClick: (String) -> Unit,
    private val metadataExtractor: MetadataExtractor,
    private val artistName: String = "",
    private val year: String? = null
) : RecyclerView.Adapter<ArtistAlbumsAdapter.AlbumViewHolder>() {

    private var files: List<MusicFile> = emptyList()

    class AlbumViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumName: TextView = itemView.findViewById(R.id.album_name)
        val albumArtwork: ImageView = itemView.findViewById(R.id.album_artwork)

        fun bind(
            album: String,
            files: List<MusicFile>,
            metadataExtractor: MetadataExtractor,
            artistName: String,
            year: String?,
            onItemClick: (String) -> Unit
        ) {
            albumName.text = album
            // Lade Albumcover für das erste Lied des Albums, das den Kriterien entspricht
            val firstFile = files.firstOrNull {
                it.album?.equals(album, ignoreCase = true) == true &&
                        (artistName.isEmpty() || it.albumArtist?.equals(artistName, ignoreCase = true) == true) &&
                        (year == null || it.year?.equals(year, ignoreCase = true) == true)
            }
            if (firstFile != null) {
                Log.d(TAG, "Loading artwork for album: $album, file: ${firstFile.uri}") // Zeile ~47
                val artworkBytes = metadataExtractor.getArtworkBytes(firstFile.uri, 0)
                if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(artworkBytes)
                        .error(android.R.drawable.ic_menu_close_clear_cancel)
                        .into(albumArtwork)
                    albumArtwork.visibility = View.VISIBLE
                    Log.d(TAG, "Artwork loaded for album: $album, size: ${artworkBytes.size} bytes") // Zeile ~55
                } else {
                    albumArtwork.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    albumArtwork.visibility = View.VISIBLE
                    Log.d(TAG, "No artwork found for album: $album") // Zeile ~59
                }
            } else {
                albumArtwork.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                albumArtwork.visibility = View.VISIBLE
                Log.w(TAG, "No matching file found for album: $album, artist: $artistName, year: $year") // Zeile ~64
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
        holder.bind(album, files, metadataExtractor, artistName, year, onItemClick)
    }

    override fun getItemCount(): Int = albums.size

    fun updateAlbums(newAlbums: List<String>, newFiles: List<MusicFile>) {
        albums = newAlbums
        files = newFiles
        Log.d(TAG, "Updated albums: ${albums.size}, files: ${files.size}")
        notifyDataSetChanged()
    }
}