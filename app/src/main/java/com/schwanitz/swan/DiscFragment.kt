package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.FragmentDiscBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DiscFragment : Fragment() {

    private var _binding: FragmentDiscBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: MusicFileAdapter
    private var discNumber: String? = null
    private var filterValue: String? = null
    private var filterType: String? = null // "album", "artist" oder "genre"
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "DiscFragment"
    private var highlightSongUri: String? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicPlaybackBinder
            musicService = binder.getService()
            isBound = true
            Log.d(TAG, "MusicPlaybackService bound for disc: $discNumber, filterType: $filterType")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            musicService = null
            Log.d(TAG, "MusicPlaybackService unbound for disc: $discNumber, filterType: $filterType")
        }
    }

    companion object {
        private const val ARG_DISC_NUMBER = "disc_number"
        private const val ARG_FILTER_VALUE = "filter_value"
        private const val ARG_FILTER_TYPE = "filter_type"
        private const val ARG_HIGHLIGHT_SONG_URI = "highlight_song_uri"

        fun newInstance(discNumber: String, filterValue: String, highlightSongUri: String? = null, filterType: String = "album"): DiscFragment {
            return DiscFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DISC_NUMBER, discNumber)
                    putString(ARG_FILTER_VALUE, filterValue)
                    putString(ARG_HIGHLIGHT_SONG_URI, highlightSongUri)
                    putString(ARG_FILTER_TYPE, filterType)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discNumber = arguments?.getString(ARG_DISC_NUMBER)
        filterValue = arguments?.getString(ARG_FILTER_VALUE)
        filterType = arguments?.getString(ARG_FILTER_TYPE) ?: "album"
        highlightSongUri = arguments?.getString(ARG_HIGHLIGHT_SONG_URI)
        viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)
        Log.d(TAG, "DiscFragment created for disc: $discNumber, filterValue: $filterValue, filterType: $filterType, highlightSongUri: $highlightSongUri")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setze die Fehlermeldung für eine leere Liste
        binding.emptyText.text = getString(R.string.no_songs_found)

        adapter = MusicFileAdapter(
            musicFiles = emptyList(),
            onItemClick = { uri ->
                Log.d(TAG, "Playing file with URI: $uri for disc: $discNumber, filterType: $filterType")
                musicService?.play(uri)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DiscFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.musicFiles.observe(viewLifecycleOwner) { files ->
                Log.d(TAG, "Received files for disc $discNumber, filterType: $filterType, total: ${files.size}, filterValue: $filterValue")
                val filteredFiles = files.filter { file ->
                    when (filterType) {
                        "album" -> {
                            val isAlbumMatch = file.album?.equals(filterValue, ignoreCase = true) == true
                            val fileDiscNumber = file.discNumber?.trim()?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()
                            val fragmentDiscNumber = discNumber?.toIntOrNull()
                            val isDiscMatch = fileDiscNumber == fragmentDiscNumber
                            Log.d(TAG, "File: ${file.name}, album: ${file.album}, disc: ${file.discNumber}, normalizedDisc: $fileDiscNumber, fragmentDisc: $fragmentDiscNumber, discMatch: $isDiscMatch, albumMatch: $isAlbumMatch")
                            isAlbumMatch && isDiscMatch
                        }
                        "artist" -> {
                            val isArtistMatch = file.artist?.equals(filterValue, ignoreCase = true) == true
                            Log.d(TAG, "File: ${file.name}, artist: ${file.artist}, artistMatch: $isArtistMatch")
                            isArtistMatch
                        }
                        "genre" -> {
                            val isGenreMatch = file.genre?.equals(filterValue, ignoreCase = true) == true
                            Log.d(TAG, "File: ${file.name}, genre: ${file.genre}, genreMatch: $isGenreMatch")
                            isGenreMatch
                        }
                        else -> false
                    }
                }.sortedBy { it.title ?: it.name } // Alphabetische Sortierung für alle Filtertypen
                Log.d(TAG, "Filtered files for disc $discNumber, filterType: $filterType: ${filteredFiles.size}, files: ${filteredFiles.map { "${it.name}, genre=${it.genre}" }}")
                adapter.updateFiles(filteredFiles)
                binding.recyclerView.visibility = if (filteredFiles.isEmpty()) View.GONE else View.VISIBLE
                binding.emptyText.visibility = if (filteredFiles.isEmpty()) View.VISIBLE else View.GONE

                // Scrolle zum Lied und hebe es hervor, wenn highlightSongUri gesetzt ist
                if (highlightSongUri != null) {
                    val highlightUri = Uri.parse(highlightSongUri)
                    val position = filteredFiles.indexOfFirst { it.uri == highlightUri }
                    Log.d(TAG, "Attempting to highlight song with URI: $highlightSongUri, position: $position")
                    if (position >= 0) {
                        binding.recyclerView.layoutManager?.scrollToPosition(position)
                        lifecycleScope.launch(Dispatchers.Main) {
                            delay(100)
                            adapter.highlightItem(binding.recyclerView, position)
                            Log.d(TAG, "Highlighted song at position $position for URI: $highlightSongUri")
                        }
                    } else {
                        Log.w(TAG, "Highlight song not found in filtered files for disc $discNumber, filterType: $filterType: $highlightSongUri")
                    }
                }
            }
        }

        Intent(requireContext(), MusicPlaybackService::class.java).also { intent ->
            requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
        _binding = null
    }
}