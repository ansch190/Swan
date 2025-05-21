package com.schwanitz.swan.ui.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.schwanitz.swan.R
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.domain.model.MusicFile
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.launch
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.first

class AddMultipleToPlaylistDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MUSIC_FILES = "music_files"
        private const val TAG = "AddMultipleToPlaylistDialog"

        fun newInstance(musicFiles: List<MusicFile>): AddMultipleToPlaylistDialogFragment {
            Log.d(TAG, "Creating AddMultipleToPlaylistDialogFragment for ${musicFiles.size} files")
            return AddMultipleToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_MUSIC_FILES, ArrayList(musicFiles))
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "onCreateDialog called")
        val musicFiles = arguments?.getParcelableArrayList<MusicFile>(ARG_MUSIC_FILES) ?: emptyList()
        if (musicFiles.isEmpty()) {
            Log.e(TAG, "No MusicFiles provided in arguments")
            dismiss()
            return AlertDialog.Builder(requireContext())
                .setMessage("Fehler: Keine Lieder ausgewählt")
                .setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
                .create()
        }

        Log.d(TAG, "MusicFiles retrieved: ${musicFiles.size} files")
        val viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                Log.d(TAG, "Dialog cancelled")
                dialog.dismiss()
            }

        // Lade Playlisten asynchron
        lifecycleScope.launch {
            try {
                val playlists = AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists().first()
                    .sortedBy { it.name.lowercase() }
                Log.d(TAG, "Loaded ${playlists.size} playlists, sorted alphabetically")
                val playlistNames = playlists.map { it.name }.toTypedArray()

                if (playlistNames.isNotEmpty()) {
                    builder.setItems(playlistNames) { _, which ->
                        val selectedPlaylist = playlists[which]
                        Log.d(TAG, "Playlist selected: ${selectedPlaylist.name} (ID: ${selectedPlaylist.id})")
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
                                    Log.d(TAG, "${musicFiles.size} songs added to playlist ${selectedPlaylist.name}")
                                } else {
                                    Log.w(TAG, "Fragment not attached, skipping Toast")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to add songs to playlist: ${e.message}", e)
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        "Fehler beim Hinzufügen zur Playlist",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Log.w(TAG, "Fragment not attached, skipping error Toast")
                                }
                            } finally {
                                dismiss()
                                Log.d(TAG, "Dialog dismissed after operation")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No playlists available")
                    builder.setMessage(R.string.no_playlists_available)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlists: ${e.message}", e)
                builder.setMessage("Fehler beim Laden der Playlisten")
            }

            // Dialog anzeigen
            val dialog = builder.create()
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.show()
            Log.d(TAG, "Dialog created and shown successfully")
        }

        // Rückgabe eines Platzhalter-Dialogs, da der echte Dialog asynchron erstellt wird
        return AlertDialog.Builder(requireContext()).create()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "Dialog started")
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        Log.d(TAG, "Dialog dismissed")
    }
}