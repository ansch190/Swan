package com.schwanitz.ui.screens.artistlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.ui.common.ArtistImageLoader
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistListViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val artistImageLoader: ArtistImageLoader
) : ViewModel() {

    private val _allArtists = MutableStateFlow<List<String>>(emptyList())
    val allArtists: StateFlow<List<String>> = _allArtists

    val artistImageUris: StateFlow<Map<String, String?>> = artistImageLoader.artistImageUris

    val errorHolder = ErrorHolder()

    fun loadArtists() {
        viewModelScope.launch {
            runCatching {
                combine(
                    musicRepository.getAllArtistNames(),
                    musicRepository.hasSongsWithNoArtist()
                ) { artists, hasNoArtist ->
                    if (hasNoArtist) listOf("") + artists else artists
                }.collect {
                    _allArtists.value = it
                    artistImageLoader.loadForArtists(it)
                }
            }.onFailure { errorHolder.emit(it) }
        }
    }
}
