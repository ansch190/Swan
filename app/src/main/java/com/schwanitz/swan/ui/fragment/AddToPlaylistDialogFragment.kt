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
        val musicFile = arguments?.getParcelable(ARG_MUSIC_FILE, MusicFile::class.java)
        val viewModel = ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireContext(), MusicRepository(requireContext()))
        ).get(MainViewModel::class.java)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_to_playlist)
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }

        // Synchrones Laden der Playlisten
        val playlists = runBlocking {
            AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists().first()
        }
        val playlistNames = playlists.map { it.name }.toTypedArray()

        if (playlistNames.isNotEmpty()) {
            builder.setItems(playlistNames) { _, which ->
                val selectedPlaylist = playlists[which]
                lifecycleScope.launch {
                    musicFile?.uri?.toString()?.let { songUri ->
                        viewModel.addSongToPlaylist(selectedPlaylist.id, songUri)
                    }
                }
                dismiss() // Dialog nach Auswahl schließen
            }
        } else {
            builder.setMessage("Keine Playlisten verfügbar")
        }

        val dialog = builder.create()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return dialog
    }
}