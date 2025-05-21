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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import android.util.Log
import android.widget.Toast

class AddToPlaylistDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MUSIC_FILE = "music_file"
        private const val TAG = "AddToPlaylistDialog"

        fun newInstance(musicFile: MusicFile): AddToPlaylistDialogFragment {
            Log.d(TAG, "Creating AddToPlaylistDialogFragment for music file: ${musicFile.name}")
            return AddToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_FILE, musicFile)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "onCreateDialog called")
        val musicFile = arguments?.getParcelable(ARG_MUSIC_FILE, MusicFile::class.java)
        if (musicFile == null) {
            Log.e(TAG, "No MusicFile provided in arguments")
            dismiss()
            return AlertDialog.Builder(requireContext())
                .setMessage("Fehler: Kein Lied ausgewählt")
                .setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
                .create()
        }

        Log.d(TAG, "MusicFile retrieved: ${musicFile.name}")
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

        // Synchrones Laden der Playlisten
        try {
            val playlists = runBlocking {
                AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists().first()
            }
            Log.d(TAG, "Loaded ${playlists.size} playlists")
            val playlistNames = playlists.map { it.name }.toTypedArray()

            if (playlistNames.isNotEmpty()) {
                builder.setItems(playlistNames) { _, which ->
                    val selectedPlaylist = playlists[which]
                    Log.d(TAG, "Playlist selected: ${selectedPlaylist.name} (ID: ${selectedPlaylist.id})")
                    lifecycleScope.launch {
                        musicFile.uri.toString().let { songUri ->
                            try {
                                viewModel.addSongToPlaylist(selectedPlaylist.id, songUri)
                                // Nur Toast anzeigen, wenn Fragment noch attached ist
                                if (isAdded) {
                                    Toast.makeText(
                                        requireContext(),
                                        getString(R.string.add_to_playlist_success, musicFile.title ?: musicFile.name, selectedPlaylist.name),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Log.d(TAG, "Song ${musicFile.name} added to playlist ${selectedPlaylist.name}")
                                } else {
                                    Log.w(TAG, "Fragment not attached, skipping Toast")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to add song to playlist: ${e.message}", e)
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
                                // Dialog erst nach Abschluss der Operation schließen
                                dismiss()
                                Log.d(TAG, "Dialog dismissed after operation")
                            }
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

        val dialog = builder.create()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        Log.d(TAG, "Dialog created successfully")
        return dialog
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