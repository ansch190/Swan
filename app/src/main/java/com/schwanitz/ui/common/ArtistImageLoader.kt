package com.schwanitz.ui.common

import com.schwanitz.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistImageLoader @Inject constructor(
    private val artistRepository: ArtistRepository
) {
    private val _artistImageUris = MutableStateFlow<Map<String, String?>>(emptyMap())
    val artistImageUris: StateFlow<Map<String, String?>> = _artistImageUris
    private val imageUris = mutableMapOf<String, String?>()

    suspend fun loadForArtists(artists: List<String>) {
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
