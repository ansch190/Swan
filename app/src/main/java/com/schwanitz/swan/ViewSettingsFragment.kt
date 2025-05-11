package com.schwanitz.swan

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.schwanitz.swan.databinding.FragmentViewSettingsBinding

class ViewSettingsFragment : DialogFragment() {

    private var _binding: FragmentViewSettingsBinding? = null
    private val binding get() = _binding!!
    private val prefs by lazy {
        requireContext().getSharedPreferences("swan_prefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentViewSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewSettingsTitle.text = getString(R.string.view_settings)
        binding.tabViewSwitch.isChecked = prefs.getBoolean("tab_view_enabled", false)
        binding.tabViewSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tab_view_enabled", isChecked).apply()
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