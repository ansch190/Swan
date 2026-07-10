package com.schwanitz.ui.screens.yearlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YearListViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _allYears = MutableStateFlow<List<Int>>(emptyList())
    val allYears: StateFlow<List<Int>> = _allYears

    fun loadYears() {
        viewModelScope.launch {
            musicRepository.getAllYears().collect {
                _allYears.value = it
            }
        }
    }
}
