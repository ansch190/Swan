package com.schwanitz.swan.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.schwanitz.swan.databinding.FragmentLibraryPathsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import android.app.AlertDialog
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.entity.LibraryPathEntity
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.ui.activity.LibraryActivity
import com.schwanitz.swan.ui.adapter.LibraryPathsAdapter
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory

class LibraryPathsFragment : DialogFragment() {

    private var _binding: FragmentLibraryPathsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private val libraryPaths = mutableListOf<LibraryPathEntity>()
    private lateinit var pathsAdapter: LibraryPathsAdapter
    private var currentWorkId: UUID? = null
    private var currentLibraryPathUri: String? = null
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

        // Initialisiere Adapter frühzeitig, um RecyclerView-Warnung zu vermeiden
        pathsAdapter = LibraryPathsAdapter(libraryPaths, ::onActionClick, requireContext())
        binding.pathsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pathsAdapter
        }

        binding.addPathButton.setOnClickListener {
            showSourceSelectionDialog()
        }
        binding.pathsTitle.text = getString(R.string.library_paths_title)

        // Beobachte Datenbankänderungen
        viewLifecycleOwner.lifecycleScope.launch {
            AppDatabase.getDatabase(requireContext()).libraryPathDao().getAllPaths().collectLatest { paths ->
                Log.d(TAG, "Loaded paths from database: ${paths.size}")
                // Behalte temporäre Pfade, die noch gescannt werden
                val tempPath = libraryPaths.find { it.uri == currentLibraryPathUri }
                libraryPaths.clear()
                libraryPaths.addAll(paths)
                if (tempPath != null && !paths.any { it.uri == tempPath.uri }) {
                    libraryPaths.add(tempPath)
                }
                pathsAdapter.notifyDataSetChanged()
            }
        }

        // Beobachte Scan-Fortschritt
        viewModel.scanProgress.observe(viewLifecycleOwner) { progress ->
            progress?.let {
                binding.scanProgressContainer.visibility = View.VISIBLE
                binding.addPathButton.isEnabled = false
                val percentage = if (it.totalFiles > 0) (it.scannedFiles * 100) / it.totalFiles else 0
                binding.scanProgressBar.progress = percentage
                binding.scanProgressText.text = getString(
                    R.string.scan_progress,
                    it.scannedFiles,
                    it.totalFiles
                )
                Log.d(TAG, "Scan progress: ${it.scannedFiles}/${it.totalFiles} ($percentage%), uri: $currentLibraryPathUri")
                pathsAdapter.setScanningPath(currentLibraryPathUri)
            } ?: run {
                binding.scanProgressContainer.visibility = View.GONE
                binding.addPathButton.isEnabled = true
                binding.scanProgressBar.progress = 0
                binding.scanProgressText.text = getString(R.string.scan_progress_initial)
                pathsAdapter.setScanningPath(null) // Kein Pfad wird gescannt
                Log.d(TAG, "Scan finished or cancelled, resetting workId and uri")
                currentWorkId = null
                currentLibraryPathUri = null
                // Schließe das Fragment, um zur Hauptansicht zurückzukehren
                if (libraryPaths.isNotEmpty()) {
                    Log.d(TAG, "Scan completed, closing fragment")
                    dismiss()
                }
            }
        }
    }

    private fun showSourceSelectionDialog() {
        val options = arrayOf(
            getString(R.string.source_local),
            getString(R.string.source_cloud)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_source_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // Lokal
                        Log.d(TAG, "Local source selected")
                        folderPicker.launch(null)
                    }
                    1 -> { // Cloud
                        Log.d(TAG, "Cloud source selected")
                        showCloudProviderDialog()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCloudProviderDialog() {
        val options = arrayOf(
            getString(R.string.cloud_provider_pcloud),
            getString(R.string.cloud_provider_nextcloud),
            getString(R.string.cloud_provider_google_drive)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_cloud_provider_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { // pCloud
                        Log.d(TAG, "pCloud selected")
                        Toast.makeText(
                            requireContext(),
                            "pCloud-Integration ist noch nicht implementiert",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> { // Nextcloud
                        Log.d(TAG, "Nextcloud selected")
                        Toast.makeText(
                            requireContext(),
                            "Nextcloud-Integration ist noch nicht implementiert",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    2 -> { // Google Drive
                        Log.d(TAG, "Google Drive selected")
                        Toast.makeText(
                            requireContext(),
                            "Google Drive-Integration ist noch nicht implementiert",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onActionClick(uri: String, isCancel: Boolean) {
        Log.d(TAG, "onActionClick called with uri: $uri, isCancel: $isCancel, currentWorkId: $currentWorkId")
        if (isCancel) {
            currentWorkId?.let { workId ->
                Log.d(TAG, "Cancelling scan for workId: $workId, uri: $uri")
                WorkManager.getInstance(requireContext()).cancelWorkById(workId)
                viewModel.cleanupCancelledScan(uri) // Bereinige Dateien und Pfad
                Toast.makeText(requireContext(), R.string.scan_cancelled, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Scan cancelled for uri: $uri")
                viewModel.scanProgress.value = null // Fortschritt sofort zurücksetzen
                // Entferne temporären Pfad aus der Liste
                val index = libraryPaths.indexOfFirst { it.uri == uri }
                if (index != -1) {
                    libraryPaths.removeAt(index)
                    pathsAdapter.notifyItemRemoved(index)
                }
            } ?: run {
                Log.w(TAG, "No workId available for cancellation, uri: $uri")
                Toast.makeText(requireContext(), "Keine laufende Scan-Aufgabe gefunden", Toast.LENGTH_SHORT).show()
                // Entferne temporären Pfad, falls vorhanden
                val index = libraryPaths.indexOfFirst { it.uri == uri }
                if (index != -1) {
                    libraryPaths.removeAt(index)
                    pathsAdapter.notifyItemRemoved(index)
                }
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                Log.d(TAG, "Removing path: $uri")
                viewModel.removeLibraryPath(uri)
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
                binding.scanProgressText.text = getString(R.string.scan_failed, e.message)
                return@launch
            }
            val uriString = uri.toString()
            Log.d(TAG, "Checking if path exists: $uriString")

            // Prüfe, ob der Pfad bereits existiert
            val pathExists = libraryPaths.any { it.uri == uriString } ||
                    AppDatabase.getDatabase(requireContext()).libraryPathDao()
                        .getAllPaths().first().any { it.uri == uriString }
            if (pathExists) {
                Log.d(TAG, "Path already exists: $uriString")
                Toast.makeText(requireContext(), R.string.path_already_exists, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val displayName = MusicRepository(requireContext()).getDisplayName(uri)
            Log.d(TAG, "Adding path: $uriString, displayName: $displayName")

            // Füge Pfad temporär zur Liste hinzu
            val tempPath = LibraryPathEntity(uriString, displayName)
            libraryPaths.add(tempPath)
            pathsAdapter.notifyItemInserted(libraryPaths.size - 1)

            // Starte den Scan über WorkManager
            currentLibraryPathUri = uriString
            pathsAdapter.setScanningPath(uriString) // Markiere Pfad als "scanning"
            currentWorkId = viewModel.addLibraryPath(uriString, displayName)
            Log.d(TAG, "Started scan with workId: $currentWorkId, uri: $uriString")
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