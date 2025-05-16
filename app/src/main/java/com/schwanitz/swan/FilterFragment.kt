package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.schwanitz.swan.databinding.FragmentFilterBinding
import kotlinx.coroutines.launch

class FilterFragment : Fragment() {

    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: FilterItemAdapter
    private var criterion: String? = null
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "FilterFragment"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicPlaybackBinder
            musicService = binder.getService()
            isBound = true
            Log.d(TAG, "MusicPlaybackService bound")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            musicService = null
            Log.d(TAG, "MusicPlaybackService unbound")
        }
    }

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
        Log.d(TAG, "Fragment created for criterion: $criterion")
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

        val artistImageRepository = ArtistImageRepository(AppDatabase.getDatabase(requireContext()))
        val metadataExtractor = MetadataExtractor(requireContext())
        adapter = FilterItemAdapter(
            items = emptyList(),
            onItemClick = { item ->
                Log.d(TAG, "Item clicked: $item for criterion: $criterion")
                if (criterion == "title") {
                    viewModel.musicFiles.value?.find { file ->
                        (file.title ?: file.name) == item
                    }?.let { musicFile ->
                        Log.d(TAG, "Playing file: ${musicFile.uri}")
                        musicService?.play(musicFile.uri)
                    } ?: Log.w(TAG, "No file found for title: $item")
                } else {
                    Log.d(TAG, "Starting SongsActivity for criterion: $criterion, value: $item")
                    val intent = Intent(context, SongsActivity::class.java).apply {
                        putExtra("criterion", criterion)
                        putExtra("value", item)
                    }
                    startActivity(intent)
                }
            },
            onItemLongClick = { item, position ->
                if (criterion == "title") {
                    Log.d(TAG, "Long click on item: $item at position: $position")
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
                adapter = FilterItemAdapter(
                    items = items,
                    onItemClick = adapter.onItemClick,
                    onItemLongClick = adapter.onItemLongClick,
                    criterion = criterion,
                    artistImageRepository = artistImageRepository,
                    metadataExtractor = metadataExtractor,
                    musicFiles = files
                )
                binding.recyclerView.adapter = adapter
                updateEmptyState(items)
                // Wende den aktuellen Suchbegriff an, falls vorhanden
                val query = (activity as? LibraryActivity)?.searchQuery?.value
                Log.d(TAG, "Applying initial filter for $criterion: $query")
                filter(query)
            }
        }

        // Binde den MusicPlaybackService
        Intent(requireContext(), MusicPlaybackService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        if (criterion == "title") {
            Log.d(TAG, "Creating context menu for view: $v")
            menu.clear() // Entferne vorhandene Einträge, um Duplikate zu vermeiden
            requireActivity().menuInflater.inflate(R.menu.context_menu, menu)
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (criterion != "title") return super.onContextItemSelected(item)
        return when (item.itemId) {
            R.id.context_info -> {
                val position = adapter.getSelectedPosition()
                if (position != RecyclerView.NO_POSITION) {
                    val selectedTitle = adapter.getItemAt(position)
                    if (selectedTitle != null) {
                        Log.d(TAG, "Context menu: Showing metadata for title: $selectedTitle at position: $position")
                        viewModel.musicFiles.value?.filter { file ->
                            (file.title ?: file.name) == selectedTitle
                        }?.let { matchingFiles ->
                            if (matchingFiles.isNotEmpty()) {
                                MetadataFragment.newInstance(matchingFiles, 0)
                                    .show(parentFragmentManager, "MetadataFragment")
                            } else {
                                Log.w(TAG, "No files found for title: $selectedTitle")
                            }
                        }
                        true
                    } else {
                        Log.w(TAG, "No item found at position: $position")
                        false
                    }
                } else {
                    Log.w(TAG, "Invalid position for context menu: $position")
                    false
                }
            }
            else -> super.onContextItemSelected(item)
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
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
        requireActivity().unregisterForContextMenu(binding.recyclerView)
        _binding = null
    }
}