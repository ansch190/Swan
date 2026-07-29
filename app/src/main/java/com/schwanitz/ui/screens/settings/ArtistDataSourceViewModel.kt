package com.schwanitz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.local.ArtistDataSourcePreferences
import com.schwanitz.domain.repository.ArtistRepository
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import com.schwanitz.ui.common.ArtistImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDataSourceUiState(
    val sources: List<SourceConfig> = emptyList(),
    val selectedSourceId: String? = null,
    val basePath: String = ArtistDataSourcePreferences.DEFAULT_BASE_PATH,
    val pendingSourceId: String? = null,
    val pendingBasePath: String = ArtistDataSourcePreferences.DEFAULT_BASE_PATH
)

@HiltViewModel
class ArtistDataSourceViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val prefs: ArtistDataSourcePreferences,
    private val artistRepository: ArtistRepository,
    private val artistImageLoader: ArtistImageLoader
) : ViewModel() {

    private val _pendingSourceId = MutableStateFlow<String?>(null)
    private val _pendingBasePath = MutableStateFlow(ArtistDataSourcePreferences.DEFAULT_BASE_PATH)

    val uiState: StateFlow<ArtistDataSourceUiState> = combine(
        sourceManager.sources,
        prefs.getSourceId(),
        prefs.getBasePath(),
        _pendingSourceId,
        _pendingBasePath
    ) { sources, sourceId, basePath, pendingId, pendingPath ->
        ArtistDataSourceUiState(
            sources = sources.filter { it.type == SourceType.WEBDAV },
            selectedSourceId = sourceId,
            basePath = basePath,
            pendingSourceId = pendingId,
            pendingBasePath = pendingPath
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ArtistDataSourceUiState())

    init {
        viewModelScope.launch {
            _pendingSourceId.value = prefs.getSourceIdSync()
            _pendingBasePath.value = prefs.getBasePathSync()
        }
    }

    fun updateSourceId(id: String?) {
        _pendingSourceId.value = id
    }

    fun updateBasePath(path: String) {
        _pendingBasePath.value = path
    }

    fun save() {
        viewModelScope.launch {
            val sourceId = _pendingSourceId.value
            val basePath = _pendingBasePath.value
            prefs.setSourceId(sourceId)
            prefs.setBasePath(basePath)
            artistImageLoader.clearCache()
            if (sourceId != null) {
                artistRepository.clearAllArtistImages()
                artistRepository.clearAllArtistBiographies()
            }
        }
    }
}
