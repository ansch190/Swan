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

class GenreArtistsFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: FilterItemAdapter
    private var genre: String? = null
    private val TAG = "GenreArtistsFragment"

    companion object {
        private const val ARG_GENRE = "genre"

        fun newInstance(genre: String): GenreArtistsFragment {
            return GenreArtistsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GENRE, genre)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        genre = arguments?.getString(ARG_GENRE)
        Log.d(TAG, "Fragment created for genre: $genre")
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
        binding.emptyText.text = getString(R.string.no_artists_available)

        val artistImageRepository = ArtistImageRepository(AppDatabase.getDatabase(requireContext()))
        val metadataExtractor = MetadataExtractor(requireContext())
        adapter = FilterItemAdapter(
            items = emptyList(),
            onItemClick = { artist ->
                Log.d(TAG, "Artist clicked: $artist")
                val intent = Intent(context, SongsActivity::class.java).apply {
                    putExtra("criterion", "artist")
                    putExtra("value", artist)
                }
                startActivity(intent)
            },
            onItemLongClick = { _, _ -> },
            criterion = "artist",
            artistImageRepository = artistImageRepository,
            metadataExtractor = metadataExtractor,
            musicFiles = viewModel.musicFiles.value ?: emptyList()
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@GenreArtistsFragment.adapter
        }

        viewModel.musicFiles.observe(viewLifecycleOwner) { files ->
            genre?.let { selectedGenre ->
                val artists = files
                    .filter { it.genre?.equals(selectedGenre, ignoreCase = true) == true }
                    .mapNotNull { it.artist }
                    .distinct()
                    .sorted()
                Log.d(TAG, "Loaded artists for genre $selectedGenre: ${artists.size}, artists: $artists")
                adapter.updateItems(artists)
                binding.recyclerView.visibility = if (artists.isEmpty()) View.GONE else View.VISIBLE
                binding.emptyText.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}