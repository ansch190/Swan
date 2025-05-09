package com.schwanitz.swan

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class FilterSelectionDialogFragment : DialogFragment() {

    private lateinit var viewModel: MainViewModel
    private val TAG = "FilterSelectionDialog"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), MainViewModelFactory(requireContext(), MusicRepository(requireContext()))).get(MainViewModel::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val filterOptions = arrayOf(
            getString(R.string.filter_by_title),
            getString(R.string.filter_by_artist),
            getString(R.string.filter_by_album),
            getString(R.string.filter_by_album_artist),
            getString(R.string.filter_by_disc_number),
            getString(R.string.filter_by_track_number),
            getString(R.string.filter_by_year),
            getString(R.string.filter_by_genre)
        )
        val filterCriteria = arrayOf(
            "title",
            "artist",
            "album",
            "albumArtist",
            "discNumber",
            "trackNumber",
            "year",
            "genre"
        )
        val selectedFilters = BooleanArray(filterOptions.size) { false }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_filters)
            .setMultiChoiceItems(filterOptions, selectedFilters) { _, which, isChecked ->
                selectedFilters[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    selectedFilters.forEachIndexed { index, isSelected ->
                        if (isSelected) {
                            val criterion = filterCriteria[index]
                            val displayName = filterOptions[index]
                            viewModel.addFilter(criterion, displayName)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
}