package com.schwanitz.swan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.FragmentLibraryPathsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryPathsFragment : DialogFragment() {

    private var _binding: FragmentLibraryPathsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private val libraryPaths = mutableListOf<LibraryPathEntity>()
    private lateinit var pathsAdapter: LibraryPathsAdapter
    private val TAG = "LibraryPathsFragment"

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { addPath(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryPathsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), MainViewModelFactory(requireContext(), MusicRepository(requireContext()))).get(MainViewModel::class.java)
        pathsAdapter = LibraryPathsAdapter(libraryPaths, ::removePath, requireContext())
        binding.pathsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pathsAdapter
        }
        binding.addPathButton.setOnClickListener {
            folderPicker.launch(null)
        }
        binding.pathsTitle.text = getString(R.string.library_paths_title)

        // Beobachte Datenbankänderungen
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).libraryPathDao().getAllPaths().collectLatest { paths ->
                Log.d(TAG, "Loaded paths from database: ${paths.size}")
                libraryPaths.clear()
                libraryPaths.addAll(paths)
                pathsAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun addPath(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "Taking permission for URI: $uri")
                context?.contentResolver?.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Log.d(TAG, "Permission taken for URI: $uri")
                // Prüfe Zugriff auf Unterordner
                try {
                    val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
                    if (documentFile?.canRead() == true) {
                        Log.d(TAG, "Can read directory: $uri")
                    } else {
                        Log.e(TAG, "Cannot read directory: $uri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check directory access for URI: $uri, error: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take permission for URI: $uri, error: ${e.message}", e)
            }
            val uriString = uri.toString()
            val displayName = MusicRepository(requireContext()).getDisplayName(uri)
            Log.d(TAG, "Adding path: $uriString, displayName: $displayName")
            viewModel.addLibraryPath(uriString, displayName)
        }
    }

    private fun removePath(uri: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Removing path: $uri")
            viewModel.removeLibraryPath(uri)
        }
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