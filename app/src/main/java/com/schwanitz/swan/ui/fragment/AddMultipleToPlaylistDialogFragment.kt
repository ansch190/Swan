package com.schwanitz.swan.ui.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.schwanitz.swan.R
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.domain.repository.MusicRepository
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.schwanitz.swan.util.Logger
import android.widget.Toast
import javax.inject.Inject

@AndroidEntryPoint
class AddMultipleToPlaylistDialogFragment : DialogFragment() {

    private val viewModel: MainViewModel by viewModels(ownerProducer = { requireActivity() })
    @Inject lateinit var repository: MusicRepository

    companion object {
        private const val ARG_MUSIC_FILES = "music_files"
        private const val TAG = "AddMultipleToPlaylistDialog"

        fun newInstance(musicFiles: List<MusicFile>): AddMultipleToPlaylistDialogFragment {
            Logger.d(TAG, "Creating AddMultipleToPlaylistDialogFragment for ${musicFiles.size} files")
            return AddMultipleToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_MUSIC_FILES, ArrayList(musicFiles))
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Logger.d(TAG, "onCreateDialog called")
        val musicFiles = arguments?.getParcelableArrayList(ARG_MUSIC_FILES, MusicFile::class.java) ?: emptyList()
        if (musicFiles.isEmpty()) {
            Logger.e(TAG, "No MusicFiles provided in arguments")
            dismiss()
            return AlertDialog.Builder(requireContext())
                .setMessage("Fehler: Keine Lieder ausgewählt")
                .setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
                .create()
        }

        Logger.d(TAG, "MusicFiles retrieved: ${musicFiles.size} files")

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                Logger.d(TAG, "Dialog cancelled")
                dialog.dismiss()
            }

        // Lade Playlisten asynchron
        lifecycleScope.launch {
            try {
                val playlists = repository.getAllPlaylists()
                    .sortedBy { it.name.lowercase() }
                Logger.d(TAG, "Loaded ${playlists.size} playlists, sorted alphabetically")
                val playlistNames = playlists.map { it.name }.toTypedArray()

                if (playlistNames.isNotEmpty()) {
                    builder.setItems(playlistNames) { _, which ->
                        val selectedPlaylist = playlists[which]
                        Logger.d(TAG, "Playlist selected: ${selectedPlaylist.name} (ID: ${selectedPlaylist.id})")
                        lifecycleScope.launch {
                            try {
                                val songUris = musicFiles.map { it.uri.toString() }
                                viewModel.addSongsToPlaylist(selectedPlaylist.id, songUris)
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.add_to_playlist_success, "${musicFiles.size} Lieder", selectedPlaylist.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Logger.d(TAG, "${musicFiles.size} songs added to playlist ${selectedPlaylist.name}")
                                } else {
                                    Logger.w(TAG, "Fragment not attached, skipping Toast")
                                }
                            } catch (e: Exception) {
                                Logger.e(TAG, "Failed to add songs to playlist: ${e.message}", e)
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Fehler beim Hinzufügen zur Playlist",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Logger.w(TAG, "Fragment not attached, skipping error Toast")
                                }
                            } finally {
                                dismiss()
                                Logger.d(TAG, "Dialog dismissed after operation")
                            }
                        }
                    }
                } else {
                    Logger.d(TAG, "No playlists available")
                    builder.setMessage(R.string.no_playlists_available)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load playlists: ${e.message}", e)
                builder.setMessage("Fehler beim Laden der Playlisten")
            }

            // Dialog anzeigen
            val dialog = builder.create()
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.show()
            Logger.d(TAG, "Dialog created and shown successfully")
        }

        // Rückgabe eines Platzhalter-Dialogs, da der echte Dialog asynchron erstellt wird
        return AlertDialog.Builder(requireContext()).create()
    }

    override fun onStart() {
        super.onStart()
        Logger.d(TAG, "Dialog started")
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        Logger.d(TAG, "Dialog dismissed")
    }
}