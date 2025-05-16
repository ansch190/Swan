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

class AddToPlaylistDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MUSIC_FILE = "music_file"

        fun newInstance(musicFile: MusicFile): AddToPlaylistDialogFragment {
            return AddToPlaylistDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_MUSIC_FILE, musicFile)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Erstelle einen Builder für den Dialog
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setMessage("Playlisten werden geladen...")
            .setNegativeButton(android.R.string.cancel, null)

        val musicFile = arguments?.getParcelable<MusicFile>(ARG_MUSIC_FILE)
        val viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)

        // Lade Playlisten asynchron und konfiguriere den Dialog
        lifecycleScope.launch {
            val playlists = AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists().first()
            val playlistNames = playlists.map { it.name }.toTypedArray()

            // Aktualisiere den Builder mit der Playlist-Liste
            builder.setMessage(null) // Entferne Lade-Nachricht
            if (playlistNames.isNotEmpty()) {
                builder.setItems(playlistNames) { _, which ->
                    val selectedPlaylist = playlists[which]
                    lifecycleScope.launch {
                        musicFile?.uri?.toString()?.let { songUri ->
                            viewModel.addSongToPlaylist(selectedPlaylist.id, songUri)
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Song zu ${selectedPlaylist.name} hinzugefügt",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } else {
                builder.setMessage("Keine Playlisten verfügbar")
            }

            // Erstelle und zeige den Dialog
            val dialog = builder.create()
            dialog.show()
            dialog.window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Gib einen temporären Dialog zurück
        return builder.create()
    }
}