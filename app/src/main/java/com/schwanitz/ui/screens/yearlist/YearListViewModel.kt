package com.schwanitz.ui.screens.yearlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YearListViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _allYears = MutableStateFlow<List<Int>>(emptyList())
    val allYears: StateFlow<List<Int>> = _allYears

    fun loadYears() {
        viewModelScope.launch {
            songRepository.getAllYears().collect {
                _allYears.value = it
            }
        }
    }
}
