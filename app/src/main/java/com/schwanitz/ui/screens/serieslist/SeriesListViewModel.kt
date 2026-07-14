package com.schwanitz.ui.screens.serieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.repository.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SeriesListViewModel @Inject constructor(
    seriesRepository: SeriesRepository
) : ViewModel() {

    val allSeries: StateFlow<List<AlbumSeries>> = seriesRepository.getAlbumSeries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
