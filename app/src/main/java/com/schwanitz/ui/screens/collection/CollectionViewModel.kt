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

private data class SongCollectionCounts(
    val albums: Int,
    val albumArtists: Int,
    val genres: Int,
    val years: Int
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    songRepository: SongRepository,
    seriesRepository: SeriesRepository
) : ViewModel() {

    private val songCounts = combine(
        songRepository.getAllAlbums(),
        songRepository.getAllAlbumArtistNames(),
        songRepository.hasAlbumsWithNoAlbumArtist(),
        songRepository.getAllGenres(),
        songRepository.getAllYears()
    ) { albums, albumArtists, hasAlbumsWithoutArtist, genres, years ->
        SongCollectionCounts(
            albums = albums.size,
            albumArtists = albumArtists.size + if (hasAlbumsWithoutArtist) 1 else 0,
            genres = genres.size,
            years = years.size
        )
    }

    val uiState: StateFlow<CollectionUiState> = combine(
        songCounts,
        seriesRepository.getAlbumSeries()
    ) { counts, series ->
        CollectionUiState(
            isLoading = false,
            albumCount = counts.albums,
            albumArtistCount = counts.albumArtists,
            genreCount = counts.genres,
            yearCount = counts.years,
            seriesCount = series.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionUiState()
    )
}
