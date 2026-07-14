package com.schwanitz.ui.screens.genrelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GenreListViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _allGenres = MutableStateFlow<List<String>>(emptyList())
    val allGenres: StateFlow<List<String>> = _allGenres

    fun loadGenres() {
        viewModelScope.launch {
            songRepository.getAllGenres().collect {
                _allGenres.value = it
            }
        }
    }
}
