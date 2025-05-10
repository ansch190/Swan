package com.schwanitz.swan

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.schwanitz.swan.databinding.FragmentFilterSettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FilterSettingsFragment : DialogFragment() {

    private var _binding: FragmentFilterSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
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
        viewModel = ViewModelProvider(requireActivity(), MainViewModelFactory(requireContext(), MusicRepository(requireContext()))).get(MainViewModel::class.java)
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
            AppDatabase.getDatabase(requireContext()).filterDao().getAllFilters().collectLatest { filterList ->
                Log.d(TAG, "Loaded filters from database: ${filterList.size}")
                filtersAdapter.setData(filterList)
            }
        }
    }

    private fun removeFilter(criterion: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Attempting to remove filter: $criterion")
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