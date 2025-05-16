package com.schwanitz.swan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.FragmentFilterBinding

class ArtistAlbumsFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ArtistAlbumsAdapter
    private var artistName: String? = null
    private val TAG = "ArtistAlbumsFragment"

    companion object {
        private const val ARG_ARTIST_NAME = "artist_name"

        fun newInstance(artistName: String): ArtistAlbumsFragment {
            return ArtistAlbumsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ARTIST_NAME, artistName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        artistName = arguments?.getString(ARG_ARTIST_NAME)
        Log.d(TAG, "Fragment created for artist: $artistName")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), MainViewModelFactory(requireContext(), MusicRepository(requireContext()))).get(MainViewModel::class.java)

        // Setze die Fehlermeldung für eine leere Liste
        binding.emptyText.text = getString(R.string.no_albums_available) // "Keine Alben verfügbar"

        adapter = ArtistAlbumsAdapter(
            albums = emptyList(),
            onItemClick = { album ->
                Log.d(TAG, "Album clicked: $album")
                val intent = Intent(context, SongsActivity::class.java).apply {
                    putExtra("criterion", "album")
                    putExtra("value", album)
                }
                startActivity(intent)
            },
            metadataExtractor = MetadataExtractor(requireContext()),
            artistName = artistName ?: ""
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ArtistAlbumsFragment.adapter
        }

        viewModel.musicFiles.observe(viewLifecycleOwner) { files ->
            artistName?.let { artist ->
                val albums = files
                    .filter { it.albumArtist?.equals(artist, ignoreCase = true) == true }
                    .mapNotNull { it.album }
                    .distinct()
                    .sorted()
                Log.d(TAG, "Loaded albums for albumArtist $artist: ${albums.size}, albums: $albums")
                adapter.updateAlbums(albums, files)
                binding.recyclerView.visibility = if (albums.isEmpty()) View.GONE else View.VISIBLE
                binding.emptyText.visibility = if (albums.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}