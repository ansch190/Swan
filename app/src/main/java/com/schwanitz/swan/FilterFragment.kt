package com.schwanitz.swan

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.FragmentFilterBinding
import kotlinx.coroutines.launch

class FilterFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: FilterItemAdapter
    private var criterion: String? = null
    private val TAG = "FilterFragment"

    companion object {
        private const val ARG_CRITERION = "criterion"

        fun newInstance(criterion: String): FilterFragment {
            val fragment = FilterFragment()
            val args = Bundle()
            args.putString(ARG_CRITERION, criterion)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        criterion = arguments?.getString(ARG_CRITERION)
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

        adapter = FilterItemAdapter(emptyList()) { item ->
            Log.d(TAG, "Item selected: $item for criterion: $criterion")
            // Hier kannst du die Logik für die Auswahl eines Elements hinzufügen
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FilterFragment.adapter
        }

        // Beobachte Musikdateien für Filterkriterien
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.musicFiles.observe(viewLifecycleOwner) { files ->
                val items = when (criterion) {
                    "title" -> files.mapNotNull { it.title ?: it.name }.distinct().sorted()
                    "artist" -> files.mapNotNull { it.artist }.distinct().sorted()
                    "album" -> files.mapNotNull { it.album }.distinct().sorted()
                    "albumArtist" -> files.mapNotNull { it.albumArtist }.distinct().sorted()
                    "discNumber" -> files.mapNotNull { it.discNumber?.toString() }.distinct().sorted()
                    "trackNumber" -> files.mapNotNull { it.trackNumber?.toString() }.distinct().sorted()
                    "year" -> files.mapNotNull { it.year?.toString() }.distinct().sorted()
                    "genre" -> files.mapNotNull { it.genre }.distinct().sorted()
                    else -> emptyList()
                }
                Log.d(TAG, "Loaded items for $criterion: ${items.size}")
                adapter.updateItems(items)
            }
        }
    }

    fun filter(query: String?) {
        val files = viewModel.musicFiles.value ?: emptyList()
        val items = when (criterion) {
            "title" -> files.mapNotNull { it.title ?: it.name }.distinct().sorted()
            "artist" -> files.mapNotNull { it.artist }.distinct().sorted()
            "album" -> files.mapNotNull { it.album }.distinct().sorted()
            "albumArtist" -> files.mapNotNull { it.albumArtist }.distinct().sorted()
            "discNumber" -> files.mapNotNull { it.discNumber?.toString() }.distinct().sorted()
            "trackNumber" -> files.mapNotNull { it.trackNumber?.toString() }.distinct().sorted()
            "year" -> files.mapNotNull { it.year?.toString() }.distinct().sorted()
            "genre" -> files.mapNotNull { it.genre }.distinct().sorted()
            else -> emptyList()
        }
        val filteredItems = if (query.isNullOrBlank()) {
            items
        } else {
            items.filter { it.contains(query, ignoreCase = true) }
        }
        Log.d(TAG, "Filtered items for $criterion: ${filteredItems.size} for query: $query")
        adapter.updateItems(filteredItems)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}