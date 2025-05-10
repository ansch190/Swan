package com.schwanitz.swan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import kotlinx.coroutines.launch

class DiscFragment : Fragment() {

    private var _binding: FragmentDiscBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: MusicFileAdapter
    private var discNumber: String? = null
    private var albumName: String? = null
    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private val TAG = "DiscFragment"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicPlaybackBinder
            musicService = binder.getService()
            isBound = true
            Log.d(TAG, "MusicPlaybackService bound for disc: $discNumber")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            musicService = null
            Log.d(TAG, "MusicPlaybackService unbound for disc: $discNumber")
        }
    }

    companion object {
        private const val ARG_DISC_NUMBER = "disc_number"
        private const val ARG_ALBUM_NAME = "album_name"

        fun newInstance(discNumber: String, albumName: String): DiscFragment {
            return DiscFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DISC_NUMBER, discNumber)
                    putString(ARG_ALBUM_NAME, albumName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discNumber = arguments?.getString(ARG_DISC_NUMBER)
        albumName = arguments?.getString(ARG_ALBUM_NAME)
        viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)
        Log.d(TAG, "DiscFragment created for disc: $discNumber, album: $albumName")
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

        adapter = MusicFileAdapter(
            musicFiles = emptyList(),
            onItemClick = { uri ->
                Log.d(TAG, "Playing file with URI: $uri for disc: $discNumber")
                musicService?.play(uri)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DiscFragment.adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.musicFiles.observe(viewLifecycleOwner) { files ->
                Log.d(TAG, "Received files for disc $discNumber: ${files.size}, discNumbers: ${files.map { it.discNumber }.distinct()}")
                val filteredFiles = files.filter { file ->
                    val isAlbumMatch = file.album?.equals(albumName, ignoreCase = true) == true
                    // Parse discNumber (e.g., "02/23" -> "2") and compare
                    val fileDiscNumber = file.discNumber?.trim()?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()?.toString()
                    val isDiscMatch = fileDiscNumber == discNumber
                    Log.d(TAG, "File: ${file.name}, album: ${file.album}, disc: ${file.discNumber}, parsedDisc: $fileDiscNumber, discMatch: $isDiscMatch, albumMatch: $isAlbumMatch")
                    isAlbumMatch && isDiscMatch
                }.sortedBy { file ->
                    file.trackNumber?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: Int.MAX_VALUE
                }
                Log.d(TAG, "Filtered files for disc $discNumber: ${filteredFiles.size}, files: ${filteredFiles.map { "${it.name}, disc=${it.discNumber}" }}")
                adapter.updateFiles(filteredFiles)
                binding.recyclerView.visibility = if (filteredFiles.isEmpty()) View.GONE else View.VISIBLE
                binding.emptyText.visibility = if (filteredFiles.isEmpty()) View.VISIBLE else View.GONE
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