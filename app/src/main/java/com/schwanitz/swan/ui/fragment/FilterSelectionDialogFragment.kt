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
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import com.schwanitz.swan.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FilterSelectionDialogFragment : DialogFragment() {

    private lateinit var viewModel: MainViewModel
    private val TAG = "FilterSelectionDialog"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), MainViewModelFactory(requireContext(), MusicRepository(requireContext()))).get(MainViewModel::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Alle möglichen Filteroptionen
        val allFilterOptions = arrayOf(
            getString(R.string.filter_by_title),
            getString(R.string.filter_by_artist),
            getString(R.string.filter_by_album),
            getString(R.string.filter_by_album_artist),
            getString(R.string.filter_by_disc_number),
            getString(R.string.filter_by_track_number),
            getString(R.string.filter_by_year),
            getString(R.string.filter_by_genre)
        )
        val allFilterCriteria = arrayOf(
            "title",
            "artist",
            "album",
            "albumArtist",
            "discNumber",
            "trackNumber",
            "year",
            "genre"
        )

        // Lade vorhandene Filter synchron
        val existingFilters = runBlocking {
            AppDatabase.getDatabase(requireContext()).filterDao().getAllFilters().first()
        }
        val existingCriteria = existingFilters.map { it.criterion }.toSet()

        // Filtere die Auswahloptionen
        val filterOptions = mutableListOf<String>()
        val filterCriteria = mutableListOf<String>()
        allFilterCriteria.forEachIndexed { index, criterion ->
            if (criterion !in existingCriteria) {
                filterOptions.add(allFilterOptions[index])
                filterCriteria.add(criterion)
            }
        }

        // Wenn keine Filter verfügbar sind, zeige eine Nachricht
        if (filterOptions.isEmpty()) {
            return AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_filters)
                .setMessage("Keine weiteren Filter verfügbar")
                .setPositiveButton(android.R.string.ok, null)
                .create()
        }

        // Erstelle den Dialog mit den verfügbaren Filtern
        val selectedFilters = BooleanArray(filterOptions.size) { false }
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_filters)
            .setMultiChoiceItems(filterOptions.toTypedArray(), selectedFilters) { _, which, isChecked ->
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