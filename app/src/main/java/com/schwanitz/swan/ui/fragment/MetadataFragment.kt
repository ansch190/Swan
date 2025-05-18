package com.schwanitz.swan.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.schwanitz.swan.databinding.FragmentMetadataTabBinding
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.usecase.Metadata
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import com.schwanitz.swan.ui.activity.SongsActivity
import com.schwanitz.swan.ui.adapter.ArtworkAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MetadataFragment : DialogFragment() {

    private var _binding: FragmentMetadataTabBinding? = null
    private val binding get() = _binding!!
    private var musicFiles: List<MusicFile> = emptyList()
    private var currentPosition: Int = 0
    private var hasTags: Boolean = false

    companion object {
        private const val ARG_MUSIC_FILES = "music_files"
        private const val ARG_POSITION = "position"
        private const val TAG = "MetadataFragment"

        fun newInstance(musicFiles: List<MusicFile>, position: Int): MetadataFragment {
            return MetadataFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_MUSIC_FILES, ArrayList(musicFiles))
                    putInt(ARG_POSITION, position)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            musicFiles = it.getParcelableArrayList(ARG_MUSIC_FILES, MusicFile::class.java) ?: emptyList()
            currentPosition = it.getInt(ARG_POSITION, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMetadataTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (musicFiles.isEmpty() || currentPosition !in musicFiles.indices) {
            Log.w(TAG, "No valid music files or position provided, dismissing fragment")
            dismiss()
            return
        }

        val musicFile = musicFiles[currentPosition]
        // Prüfe asynchron, ob Tags vorhanden sind
        viewLifecycleOwner.lifecycleScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                MetadataExtractor(requireContext()).extractMetadata(musicFile.uri)
            }
            hasTags = hasTags(metadata)
            Log.d(TAG, "File: ${musicFile.name}, hasTags: $hasTags")

            // Initialisiere Tabs basierend auf Tag-Verfügbarkeit
            val adapter = MetadataPagerAdapter(this@MetadataFragment, musicFile, hasTags)
            binding.viewPager.adapter = adapter
            binding.viewPager.offscreenPageLimit = if (hasTags) 2 else 1
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> if (hasTags) "Metadaten" else "Technisch"
                    1 -> "Technisch"
                    else -> ""
                }
            }.attach()
        }
    }

    private fun hasTags(metadata: Metadata): Boolean {
        return metadata.title.isNotEmpty() ||
                metadata.artist.isNotEmpty() ||
                metadata.album.isNotEmpty() ||
                metadata.albumArtist.isNotEmpty() ||
                metadata.discNumber.isNotEmpty() ||
                metadata.trackNumber.isNotEmpty() ||
                metadata.year.isNotEmpty() ||
                metadata.genre.isNotEmpty() ||
                metadata.artworkCount > 0
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MetadataPagerAdapter(
    fragment: DialogFragment,
    private val musicFile: MusicFile,
    private val hasTags: Boolean
) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = if (hasTags) 2 else 1

    override fun createFragment(position: Int): androidx.fragment.app.Fragment {
        return when (position) {
            0 -> if (hasTags) MetadataTagsFragment.newInstance(musicFile) else MetadataTechnicalFragment.newInstance(musicFile)
            1 -> MetadataTechnicalFragment.newInstance(musicFile)
            else -> throw IllegalStateException("Invalid position")
        }
    }
}

class MetadataTagsFragment : androidx.fragment.app.Fragment() {
    companion object {
        private const val ARG_MUSIC_FILE = "music_file"
        private const val TAG = "MetadataTagsFragment"

        fun newInstance(musicFile: MusicFile): MetadataTagsFragment {
            return MetadataTagsFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_FILE, musicFile)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = com.schwanitz.swan.databinding.FragmentMetadataBinding.inflate(inflater, container, false)
        val musicFile = arguments?.getParcelable(ARG_MUSIC_FILE, MusicFile::class.java)
        musicFile?.let {
            binding.titleValue.text = it.title ?: "Unbekannt"
            binding.artistValue.text = it.artist ?: "Unbekannt"
            binding.albumValue.text = it.album ?: "Unbekannt"
            binding.albumArtistValue.text = it.albumArtist ?: "Unbekannt"
            binding.discNumberValue.text = it.discNumber ?: "Unbekannt"
            binding.trackNumberValue.text = it.trackNumber ?: "Unbekannt"
            binding.yearValue.text = it.year ?: "Unbekannt"
            binding.genreValue.text = it.genre ?: "Unbekannt"

            // Klick-Listener für den Album-TextView
            binding.albumValue.setOnClickListener { _ ->
                if (!it.album.isNullOrBlank() && it.album != "Unbekannt") {
                    Log.d(TAG, "Navigating to album: ${it.album} with song URI: ${it.uri}")
                    val intent = Intent(context, SongsActivity::class.java).apply {
                        putExtra("criterion", "album")
                        putExtra("value", it.album)
                        putExtra("highlight_song_uri", it.uri.toString())
                    }
                    startActivity(intent)
                } else {
                    Log.w(TAG, "No album name available for navigation")
                }
            }

            // Klick-Listener für den Künstler-TextView
            binding.artistValue.setOnClickListener { _ ->
                if (!it.artist.isNullOrBlank() && it.artist != "Unbekannt") {
                    Log.d(TAG, "Navigating to artist: ${it.artist} with song URI: ${it.uri}")
                    val intent = Intent(context, SongsActivity::class.java).apply {
                        putExtra("criterion", "artist")
                        putExtra("value", it.artist)
                        putExtra("highlight_song_uri", it.uri.toString())
                    }
                    startActivity(intent)
                } else {
                    Log.w(TAG, "No artist name available for navigation")
                }
            }

            // Klick-Listener für den Jahr-TextView
            binding.yearValue.setOnClickListener { _ ->
                if (!it.year.isNullOrBlank() && it.year != "Unbekannt") {
                    Log.d(TAG, "Navigating to year: ${it.year} with song URI: ${it.uri}")
                    val intent = Intent(context, SongsActivity::class.java).apply {
                        putExtra("criterion", "year")
                        putExtra("value", it.year)
                        putExtra("highlight_song_uri", it.uri.toString())
                    }
                    startActivity(intent)
                } else {
                    Log.w(TAG, "No year available for navigation")
                }
            }

            // Asynchrones Laden der Bilder aus ID3v2.4-Tags
            val metadataExtractor = MetadataExtractor(requireContext())
            try {
                val metadata = metadataExtractor.extractMetadata(it.uri)
                Log.d(TAG, "Artwork count: ${metadata.artworkCount} for file: ${it.name}, URI: ${it.uri}")
                val artworkAdapter = ArtworkAdapter(requireContext())
                binding.artworkRecyclerView.apply {
                    layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                    adapter = artworkAdapter
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    val artworks = mutableListOf<ByteArray>()
                    withContext(Dispatchers.IO) {
                        // Begrenze auf maximal 2 Bilder für die Anzeige
                        for (index in 0 until minOf(metadata.artworkCount, 2)) {
                            val artwork = metadataExtractor.getArtworkBytes(it.uri, index)
                            if (artwork != null && artwork.isNotEmpty()) {
                                Log.d(TAG, "Loaded artwork at index $index, size: ${artwork.size} bytes for file: ${it.name}")
                                artworks.add(artwork)
                            } else {
                                Log.d(TAG, "No artwork at index $index for file: ${it.name}")
                            }
                        }
                    }
                    Log.d(TAG, "Total artworks loaded: ${artworks.size} for file: ${it.name}")
                    if (artworks.isEmpty()) {
                        binding.artworkRecyclerView.visibility = View.GONE
                    } else {
                        binding.artworkRecyclerView.visibility = View.VISIBLE
                        // Übergib alle verfügbaren Bilder an den Adapter
                        val allArtworks = mutableListOf<ByteArray>()
                        withContext(Dispatchers.IO) {
                            for (index in 0 until metadata.artworkCount) {
                                val artwork = metadataExtractor.getArtworkBytes(it.uri, index)
                                if (artwork != null && artwork.isNotEmpty()) {
                                    allArtworks.add(artwork)
                                }
                            }
                        }
                        artworkAdapter.setData(artworks, allArtworks)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting metadata for ${it.name}: ${e.message}", e)
                binding.artworkRecyclerView.visibility = View.GONE
            }
        }
        return binding.root
    }
}

class MetadataTechnicalFragment : androidx.fragment.app.Fragment() {
    companion object {
        private const val ARG_MUSIC_FILE = "music_file"
        private const val TAG = "MetadataTechnicalFragment"

        fun newInstance(musicFile: MusicFile): MetadataTechnicalFragment {
            return MetadataTechnicalFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_FILE, musicFile)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = com.schwanitz.swan.databinding.FragmentTechnicalBinding.inflate(inflater, container, false)
        val musicFile = arguments?.getParcelable(ARG_MUSIC_FILE, MusicFile::class.java)
        musicFile?.let {
            binding.filenameValue.text = it.name
            binding.pathValue.text = it.uri.path?.let { path ->
                val cleanPath = path.substringAfterLast("primary:", path)
                cleanPath.substringBeforeLast("/")
            } ?: "Unbekannt"
            binding.fileSizeValue.text = formatFileSize(it.fileSize)
            binding.codecValue.text = it.audioCodec ?: "Unbekannt"
            binding.sampleRateValue.text = "${it.sampleRate} Hz"
            binding.bitrateValue.text = "${it.bitrate} kbps"
            binding.tagVersionValue.text = it.tagVersion?.takeIf { it.isNotEmpty() } ?: "Unbekannt"
        }
        return binding.root
    }

    private fun formatFileSize(size: Int): String {
        return when {
            size < 1024 -> String.format("%.1f B", size.toDouble())
            size < 1024 * 1024 -> String.format("%.1f KB", size.toDouble() / 1024)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size.toDouble() / (1024 * 1024))
            else -> String.format("%.1f GB", size.toDouble() / (1024 * 1024 * 1024))
        }
    }
}