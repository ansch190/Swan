package com.schwanitz.swan.ui.fragment

import android.content.Intent
import android.os.Bundle
import com.schwanitz.swan.util.Logger
import android.view.ContextMenu
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.R
import com.schwanitz.swan.databinding.FragmentFilterBinding
import com.schwanitz.swan.domain.repository.ArtistImageRepository
import javax.inject.Inject
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import com.schwanitz.swan.ui.activity.LibraryActivity
import com.schwanitz.swan.ui.activity.SongsActivity
import com.schwanitz.swan.ui.adapter.FilterItemAdapter
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilterFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels(ownerProducer = { requireActivity() })
    private lateinit var adapter: FilterItemAdapter
    @Inject lateinit var artistImageRepository: ArtistImageRepository
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
        Logger.d(TAG, "Fragment created for criterion: $criterion")
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

        val metadataExtractor = MetadataExtractor.getInstance(requireContext())
        adapter = FilterItemAdapter(
            items = emptyList(),
            onItemClick = { item ->
                Logger.d(TAG, "Item clicked: $item for criterion: $criterion")
                if (criterion == "title") {
                    viewModel.musicFiles.value?.find { file ->
                        (file.title ?: file.name) == item
                    }?.let { musicFile ->
                        Logger.d(TAG, "Playing file: ${musicFile.uri}")
                        viewModel.musicService?.play(musicFile.uri)
                    } ?: Logger.w(TAG, "No file found for title: $item")
                } else {
                    Logger.d(TAG, "Starting SongsActivity for criterion: $criterion, value: $item")
                    val intent = Intent(context, SongsActivity::class.java).apply {
                        putExtra("criterion", criterion)
                        putExtra("value", item)
                    }
                    startActivity(intent)
                }
            },
            onItemLongClick = { item, position ->
                if (criterion == "title") {
                    Logger.d(TAG, "Long click on item: $item at position: $position")
                    adapter.setSelectedPosition(position)
                    binding.recyclerView.showContextMenu()
                }
            },
            criterion = criterion,
            artistImageRepository = artistImageRepository,
            metadataExtractor = metadataExtractor,
            musicFiles = viewModel.musicFiles.value ?: emptyList()
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@FilterFragment.adapter
            registerForContextMenu(this)
        }

        // Beobachte Musikdateien für Filterkriterien
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.musicFiles.collectLatest { files ->
                val items = when (criterion) {
                    "title" -> files.mapNotNull { it.title ?: it.name }.distinct().sorted()
                    "artist" -> files.mapNotNull { it.artist }.distinct().sorted()
                    "album" -> files.mapNotNull { it.album }.distinct().sorted()
                    "albumArtist" -> files.mapNotNull { it.albumArtist }.distinct().sorted()
                    "year" -> files.mapNotNull { it.year }.distinct().sorted().map { it.toString() }
                    "genre" -> files.mapNotNull { it.genre }.distinct().sorted()
                    else -> emptyList()
                }
                Logger.d(TAG, "Loaded items for $criterion: ${items.size}")
                adapter.updateItems(items)
                updateEmptyState(items)
                // Wende den aktuellen Suchbegriff an, falls vorhanden
                val query = (activity as? LibraryActivity)?.searchQuery?.value
                Logger.d(TAG, "Applying initial filter for $criterion: $query")
                filter(query)
            }
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (criterion == "title") {
            Logger.d(TAG, "Creating context menu for view: $v")
            menu.clear() // Entferne vorhandene Einträge, um Duplikate zu vermeiden
            requireActivity().menuInflater.inflate(R.menu.context_menu, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (criterion != "title") return super.onContextItemSelected(item)
        Logger.d(TAG, "Context menu item selected: ${item.itemId}, title: ${item.title}")
        return when (item.itemId) {
            R.id.context_info -> {
                val position = adapter.getSelectedPosition()
                Logger.d(TAG, "Processing context_info for position: $position")
                if (position != RecyclerView.NO_POSITION) {
                    val selectedTitle = adapter.getItemAt(position)
                    if (selectedTitle != null) {
                        Logger.d(TAG, "Showing metadata for title: $selectedTitle at position: $position")
                        viewModel.musicFiles.value?.filter { file ->
                            (file.title ?: file.name) == selectedTitle
                        }?.let { matchingFiles ->
                            if (matchingFiles.isNotEmpty()) {
                                try {
                                    MetadataFragment.newInstance(matchingFiles, 0)
                                        .show(parentFragmentManager, "MetadataFragment")
                                    Logger.d(TAG, "MetadataFragment shown successfully")
                                } catch (e: Exception) {
                                    Logger.e(TAG, "Failed to show MetadataFragment: ${e.message}", e)
                                }
                            } else {
                                Logger.w(TAG, "No files found for title: $selectedTitle")
                            }
                        }
                        true
                    } else {
                        Logger.w(TAG, "No item found at position: $position")
                        false
                    }
                } else {
                    Logger.w(TAG, "Invalid position for context menu: $position")
                    false
                }
            }
            R.id.context_add_to_playlist -> {
                val position = adapter.getSelectedPosition()
                Logger.d(TAG, "Processing context_add_to_playlist for position: $position")
                if (position != RecyclerView.NO_POSITION) {
                    val selectedTitle = adapter.getItemAt(position)
                    if (selectedTitle != null) {
                        val musicFile = viewModel.musicFiles.value?.find { file ->
                            (file.title ?: file.name) == selectedTitle
                        }
                        if (musicFile != null) {
                            Logger.d(TAG, "Opening AddToPlaylistDialogFragment for: ${musicFile.name}")
                            try {
                                AddToPlaylistDialogFragment.newInstance(musicFile)
                                    .show(parentFragmentManager, "AddToPlaylistDialog")
                                Logger.d(TAG, "AddToPlaylistDialogFragment shown successfully")
                            } catch (e: Exception) {
                                Logger.e(TAG, "Failed to show AddToPlaylistDialogFragment: ${e.message}", e)
                                Toast.makeText(requireContext(), "Fehler beim Öffnen des Dialogs", Toast.LENGTH_SHORT).show()
                            }
                            true
                        } else {
                            Logger.w(TAG, "No MusicFile found for title: $selectedTitle")
                            false
                        }
                    } else {
                        Logger.w(TAG, "No item found at position: $position")
                        false
                    }
                } else {
                    Logger.w(TAG, "Invalid position for context menu: $position")
                    false
                }
            }
            else -> {
                Logger.w(TAG, "Unknown menu item selected: ${item.itemId}")
                super.onContextItemSelected(item)
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
            "year" -> files.mapNotNull { it.year }.distinct().sorted().map { it.toString() }
            "genre" -> files.mapNotNull { it.genre }.distinct().sorted()
            else -> emptyList()
        }
        val filteredItems = if (query.isNullOrBlank()) {
            items
        } else {
            items.filter { it.contains(query, ignoreCase = true) }
        }
        Logger.d(TAG, "Filtered items for $criterion: ${filteredItems.size} for query: $query")
        adapter.updateItems(filteredItems)
        updateEmptyState(filteredItems)
    }

    private fun updateEmptyState(items: List<String>) {
        if (items.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyText.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().unregisterForContextMenu(binding.recyclerView)
        _binding = null
    }
}