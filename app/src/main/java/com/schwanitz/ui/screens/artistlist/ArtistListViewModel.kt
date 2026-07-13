package com.schwanitz.ui.screens.artistlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.ArtistRepository
import com.schwanitz.domain.repository.MusicRepository
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
    private val artistRepository: ArtistRepository
) : ViewModel() {

    private val _allArtists = MutableStateFlow<List<String>>(emptyList())
    val allArtists: StateFlow<List<String>> = _allArtists

    private val _artistImageUris = MutableStateFlow<Map<String, String?>>(emptyMap())
    val artistImageUris: StateFlow<Map<String, String?>> = _artistImageUris
    private val imageUris = mutableMapOf<String, String?>()

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
                    loadArtistImages(it)
                }
            }.onFailure { errorHolder.emit(it) }
        }
    }

    private suspend fun loadArtistImages(artists: List<String>) {
        for (artist in artists) {
            try {
                if (artist.isBlank()) {
                    imageUris[artist] = null
                    _artistImageUris.value = imageUris.toMap()
                    continue
                }
                if (!imageUris.containsKey(artist)) {
                    imageUris[artist] = null
                    _artistImageUris.value = imageUris.toMap()
                    val artistEntity = artistRepository.getArtistByName(artist)
                    val uri = artistEntity?.let { artistRepository.getArtistImageSmall(it.id) }
                    imageUris[artist] = uri
                    _artistImageUris.value = imageUris.toMap()
                }
            } catch (_: Exception) {
                continue
            }
        }
    }
}
