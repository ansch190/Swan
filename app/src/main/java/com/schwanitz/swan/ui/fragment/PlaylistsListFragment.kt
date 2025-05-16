package com.schwanitz.swan.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.databinding.FragmentPlaylistsListBinding
import com.schwanitz.swan.ui.adapter.PlaylistAdapter
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlaylistsListFragment : Fragment() {

    private var _binding: FragmentPlaylistsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: PlaylistAdapter
    private val searchQuery = MutableStateFlow<String?>(null)
    private val TAG = "PlaylistsListFragment"
    private var allPlaylists: List<PlaylistEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)

        adapter = PlaylistAdapter(playlists = emptyList())
        binding.playlistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PlaylistsListFragment.adapter
        }

        // Beobachte Playlists aus der Datenbank
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists()
                .collectLatest { playlists ->
                    Log.d(TAG, "Received ${playlists.size} playlists from database")
                    allPlaylists = playlists.sortedBy { it.name.lowercase() }
                    applyFilter(searchQuery.value)
                }
        }

        // Beobachte Suchbegriff-Änderungen
        viewLifecycleOwner.lifecycleScope.launch {
            searchQuery.collectLatest { query ->
                Log.d(TAG, "Search query changed: $query")
                applyFilter(query)
            }
        }

        // FAB-Logik
        val fab = activity?.findViewById<View>(R.id.fab)
        fab?.visibility = View.VISIBLE
        fab?.setOnClickListener {
            showCreatePlaylistDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        // Verstecke FAB, wenn Fragment nicht sichtbar
        activity?.findViewById<View>(R.id.fab)?.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun updateSearchQuery(query: String?) {
        Log.d(TAG, "Updating search query: $query")
        searchQuery.value = query
    }

    fun getPlaylistAdapter(): PlaylistAdapter? {
        return binding.playlistsRecyclerView.adapter as? PlaylistAdapter
    }

    private fun applyFilter(query: String?) {
        val filteredPlaylists = if (query.isNullOrEmpty()) {
            allPlaylists
        } else {
            allPlaylists.filter { it.name.contains(query, ignoreCase = true) }
        }
        Log.d(TAG, "Applying filter with query '$query': ${filteredPlaylists.size} playlists")
        adapter.updatePlaylists(filteredPlaylists)
        binding.playlistsRecyclerView.visibility = if (filteredPlaylists.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyText.visibility = if (filteredPlaylists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showCreatePlaylistDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.create_playlist_dialog_title)
        builder.setMessage(R.string.create_playlist_dialog_message)

        val input = android.widget.EditText(requireContext())
        input.hint = getString(R.string.playlist_name_hint)
        input.setPadding(32, 16, 32, 16)
        builder.setView(input)

        builder.setPositiveButton(R.string.create_playlist) { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.createPlaylist(name, emptyList())
                }
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    R.string.create_playlist_error_empty,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }
}