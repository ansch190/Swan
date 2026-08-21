package com.schwanitz.ui.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.SeriesRepository
import com.schwanitz.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CollectionUiState(
    val isLoading: Boolean = true,
    val albumCount: Int = 0,
    val albumArtistCount: Int = 0,
    val genreCount: Int = 0,
    val yearCount: Int = 0,
    val seriesCount: Int = 0
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    songRepository: SongRepository,
    seriesRepository: SeriesRepository
) : ViewModel() {

    val uiState: StateFlow<CollectionUiState> = combine(
        songRepository.observeCollectionCounts(),
        seriesRepository.observeSeriesCount()
    ) { counts, seriesCount ->
        CollectionUiState(
            isLoading = false,
            albumCount = counts.albums,
            albumArtistCount = counts.albumArtists,
            genreCount = counts.genres,
            yearCount = counts.years,
            seriesCount = seriesCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionUiState()
    )
}
