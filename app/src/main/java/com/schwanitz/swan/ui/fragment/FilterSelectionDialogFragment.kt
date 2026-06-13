package com.schwanitz.swan.ui.fragment

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.schwanitz.swan.R
import com.schwanitz.swan.domain.repository.MusicRepository
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FilterSelectionDialogFragment : DialogFragment() {

    private val viewModel: MainViewModel by viewModels(ownerProducer = { requireActivity() })
    @Inject lateinit var repository: MusicRepository
    private val TAG = "FilterSelectionDialog"
    private var existingCriteria: Set<String>? = null
    private var isLoading = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            existingCriteria = repository.getFiltersOnce().map { it.criterion }.toSet()
            isLoading = false
            if (dialog != null) {
                rebuildDialog()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return if (isLoading) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_filters)
                .setMessage(R.string.loading)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        } else {
            buildSelectionDialog()
        }
    }

    private fun rebuildDialog() {
        if (!isAdded) return
        val newDialog = buildSelectionDialog()
        newDialog.show()
        dialog?.dismiss()
    }

    private fun buildSelectionDialog(): AlertDialog {
        val allFilterOptions = arrayOf(
            getString(R.string.filter_by_title),
            getString(R.string.filter_by_artist),
            getString(R.string.filter_by_album),
            getString(R.string.filter_by_album_artist),
            getString(R.string.filter_by_year),
            getString(R.string.filter_by_genre)
        )
        val allFilterCriteria = arrayOf(
            "title",
            "artist",
            "album",
            "albumArtist",
            "year",
            "genre"
        )

        val criteria = existingCriteria ?: emptySet()

        val filterOptions = mutableListOf<String>()
        val filterCriteria = mutableListOf<String>()
        allFilterCriteria.forEachIndexed { index, criterion ->
            if (criterion !in criteria) {
                filterOptions.add(allFilterOptions[index])
                filterCriteria.add(criterion)
            }
        }

        if (filterOptions.isEmpty()) {
            return AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_filters)
                .setMessage("Keine weiteren Filter verfügbar")
                .setPositiveButton(android.R.string.ok, null)
                .create()
        }

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