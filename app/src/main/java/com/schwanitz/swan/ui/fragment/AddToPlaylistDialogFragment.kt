package com.schwanitz.swan.ui.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.entity.PlaylistEntity
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.repository.MusicRepository
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.schwanitz.swan.util.Logger
import android.widget.Toast

@AndroidEntryPoint
class AddToPlaylistDialogFragment : DialogFragment() {

    @Inject lateinit var repository: MusicRepository
    private var playlists: List<PlaylistEntity> = emptyList()
    private var isLoading = true
    private var loadError: String? = null
    private val viewModel: MainViewModel by viewModels(ownerProducer = { requireActivity() })

    companion object {
        private const val ARG_MUSIC_FILE = "music_file"
        private const val TAG = "AddToPlaylistDialog"

        fun newInstance(musicFile: MusicFile): AddToPlaylistDialogFragment {
            Logger.d(TAG, "Creating AddToPlaylistDialogFragment for music file: ${musicFile.name}")
            return AddToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_FILE, musicFile)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                playlists = repository.getAllPlaylists().sortedBy { it.name.lowercase() }
                isLoading = false
                Logger.d(TAG, "Loaded ${playlists.size} playlists, sorted alphabetically")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load playlists: ${e.message}", e)
                loadError = e.message
                isLoading = false
            }
            if (dialog != null) {
                rebuildDialog(musicFile)
            }
        }
    }

    private val musicFile: MusicFile?
        get() = arguments?.getParcelable(ARG_MUSIC_FILE, MusicFile::class.java)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Logger.d(TAG, "onCreateDialog called")
        val file = musicFile
        if (file == null) {
            Logger.e(TAG, "No MusicFile provided in arguments")
            dismiss()
            return AlertDialog.Builder(requireContext())
                .setMessage("Fehler: Kein Lied ausgewählt")
                .setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
                .create()
        }

        Logger.d(TAG, "MusicFile retrieved: ${file.name}")

        return if (isLoading) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_to_playlist)
                .setMessage(R.string.loading_playlists)
                .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
                .create()
        } else {
            buildSelectionDialog(file)
        }
    }

    private fun rebuildDialog(musicFile: MusicFile?) {
        val file = musicFile ?: return
        if (!isAdded) return
        val newDialog = buildSelectionDialog(file)
        newDialog.show()
        // Dismiss the old loading dialog
        dialog?.dismiss()
    }

    private fun buildSelectionDialog(musicFile: MusicFile): AlertDialog {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }

        if (loadError != null) {
            builder.setMessage("Fehler beim Laden der Playlisten: $loadError")
        } else if (playlists.isEmpty()) {
            builder.setMessage(R.string.no_playlists_available)
        } else {
            val playlistNames = playlists.map { it.name }.toTypedArray()
            builder.setItems(playlistNames) { _, which ->
                val selectedPlaylist = playlists[which]
                Logger.d(TAG, "Playlist selected: ${selectedPlaylist.name} (ID: ${selectedPlaylist.id})")
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        viewModel.addSongToPlaylist(selectedPlaylist.id, musicFile.uri.toString())
                    }
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.add_to_playlist_success, musicFile.title ?: musicFile.name, selectedPlaylist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    dismiss()
                }
            }
        }

        return builder.create()
    }
}