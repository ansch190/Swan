package com.schwanitz.swan.ui.fragment

import android.os.Bundle
import com.schwanitz.swan.util.Logger
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.R
import com.schwanitz.swan.databinding.FragmentFilterSettingsBinding
import com.schwanitz.swan.domain.repository.MusicRepository
import javax.inject.Inject
import com.schwanitz.swan.ui.adapter.FilterSettingsAdapter
import com.schwanitz.swan.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilterSettingsFragment : DialogFragment() {

    private var _binding: FragmentFilterSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels(ownerProducer = { requireActivity() })
    @Inject lateinit var repository: MusicRepository
    private lateinit var filtersAdapter: FilterSettingsAdapter
    private val TAG = "FilterSettingsFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        filtersAdapter = FilterSettingsAdapter(requireContext(), ::removeFilter)
        binding.filtersRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = filtersAdapter
        }
        binding.addFilterButton.setOnClickListener {
            FilterSelectionDialogFragment().show(parentFragmentManager, "FilterSelectionDialog")
        }
        binding.filtersTitle.text = getString(R.string.filter_settings_title)

        // Beobachte Datenbankänderungen
        viewLifecycleOwner.lifecycleScope.launch {
            repository.getAllFilters().collectLatest { filterList ->
                Logger.d(TAG, "Loaded filters from database: ${filterList.size}")
                filtersAdapter.setData(filterList)
            }
        }
    }

    private fun removeFilter(criterion: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            Logger.d(TAG, "Attempting to remove filter: $criterion")
            val removed = viewModel.removeFilter(criterion)
            if (!removed) {
                Toast.makeText(requireContext(), R.string.cannot_remove_last_filter, Toast.LENGTH_SHORT).show()
            }
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