package com.schwanitz.swan

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.FragmentDiscBinding

class DiscFragment : Fragment() {

    private var _binding: FragmentDiscBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: MusicFileAdapter
    private var discNumber: String? = null
    private var albumName: String? = null

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
        viewModel = ViewModelProvider(requireActivity(), MainViewModelFactory(requireContext(), MusicRepository(requireContext()))).get(MainViewModel::class.java)

        adapter = MusicFileAdapter(
            musicFiles = emptyList(),
            onItemClick = { uri ->
                Log.d("DiscFragment", "Playing file with URI: $uri")
                // Hier kannst du die Wiedergabe implementieren
            },
            onShowMetadata = { musicFile ->
                Log.d("DiscFragment", "Showing metadata for file: ${musicFile.name}")
                // Hier kannst du die Metadaten anzeigen
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DiscFragment.adapter
        }

        viewModel.musicFiles.observe(viewLifecycleOwner) { files ->
            val filteredFiles = files.filter { file ->
                file.album?.equals(albumName, ignoreCase = true) == true && file.discNumber == discNumber
            }.sortedBy { file ->
                file.trackNumber?.toIntOrNull() ?: Int.MAX_VALUE
            }
            adapter.updateFiles(filteredFiles)
            binding.recyclerView.visibility = if (filteredFiles.isEmpty()) View.GONE else View.VISIBLE
            binding.emptyText.visibility = if (filteredFiles.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}