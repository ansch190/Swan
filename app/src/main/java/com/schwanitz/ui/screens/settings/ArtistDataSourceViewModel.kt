package com.schwanitz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.local.ArtistDataSourcePreferences
import com.schwanitz.data.local.CredentialStore
import com.schwanitz.data.local.SharedImportPreferences
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

private const val MASKED = "••••••••"

data class ArtistDataSourceUiState(
    val sources: List<SourceConfig> = emptyList(),
    val selectedSourceId: String? = null,
    val basePath: String = ArtistDataSourcePreferences.DEFAULT_BASE_PATH,
    val pendingSourceId: String? = null,
    val pendingBasePath: String = ArtistDataSourcePreferences.DEFAULT_BASE_PATH,
    val pendingDiscogsKey: String = "",
    val pendingDiscogsSecret: String = "",
    val pendingLastfmKey: String = "",
    val pendingGeniusToken: String = "",
    val areApiKeysHidden: Boolean = false
)

@HiltViewModel
class ArtistDataSourceViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val prefs: ArtistDataSourcePreferences,
    private val credentialStore: CredentialStore,
    private val sharedImportPreferences: SharedImportPreferences,
    private val artistRepository: ArtistRepository,
    private val artistImageLoader: ArtistImageLoader
) : ViewModel() {

    private val _pendingSourceId = MutableStateFlow<String?>(null)
    private val _pendingBasePath = MutableStateFlow(ArtistDataSourcePreferences.DEFAULT_BASE_PATH)
    private val _pendingDiscogsKey = MutableStateFlow("")
    private val _pendingDiscogsSecret = MutableStateFlow("")
    private val _pendingLastfmKey = MutableStateFlow("")
    private val _pendingGeniusToken = MutableStateFlow("")
    private val _areApiKeysHidden = MutableStateFlow(false)

    val uiState: StateFlow<ArtistDataSourceUiState> = combine(
        listOf(
            sourceManager.sources,
            prefs.getSourceId(),
            prefs.getBasePath(),
            _pendingSourceId,
            _pendingBasePath,
            _pendingDiscogsKey,
            _pendingDiscogsSecret,
            _pendingLastfmKey,
            _pendingGeniusToken,
            _areApiKeysHidden
        )
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val hidden = array[9] as Boolean
        ArtistDataSourceUiState(
            sources = (array[0] as List<SourceConfig>).filter { it.type == SourceType.WEBDAV },
            selectedSourceId = array[1] as String?,
            basePath = array[2] as String,
            pendingSourceId = array[3] as String?,
            pendingBasePath = array[4] as String,
            pendingDiscogsKey = if (hidden) MASKED else (array[5] as String),
            pendingDiscogsSecret = if (hidden) MASKED else (array[6] as String),
            pendingLastfmKey = if (hidden) MASKED else (array[7] as String),
            pendingGeniusToken = if (hidden) MASKED else (array[8] as String),
            areApiKeysHidden = hidden
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ArtistDataSourceUiState())

    init {
        viewModelScope.launch {
            _pendingSourceId.value = prefs.getSourceIdSync()
            _pendingBasePath.value = prefs.getBasePathSync()
            _areApiKeysHidden.value = sharedImportPreferences.isAnyApiKeysHidden()
            if (!_areApiKeysHidden.value) {
                _pendingDiscogsKey.value = credentialStore.getApiDiscogsKey() ?: ""
                _pendingDiscogsSecret.value = credentialStore.getApiDiscogsSecret() ?: ""
                _pendingLastfmKey.value = credentialStore.getApiLastfmKey() ?: ""
                _pendingGeniusToken.value = credentialStore.getApiGeniusToken() ?: ""
            }
        }
    }

    fun updateSourceId(id: String?) {
        _pendingSourceId.value = id
    }

    fun updateBasePath(path: String) {
        _pendingBasePath.value = path
    }

    fun updateDiscogsKey(key: String) {
        if (_areApiKeysHidden.value) return
        _pendingDiscogsKey.value = key
    }

    fun updateDiscogsSecret(secret: String) {
        if (_areApiKeysHidden.value) return
        _pendingDiscogsSecret.value = secret
    }

    fun updateLastfmKey(key: String) {
        if (_areApiKeysHidden.value) return
        _pendingLastfmKey.value = key
    }

    fun updateGeniusToken(token: String) {
        if (_areApiKeysHidden.value) return
        _pendingGeniusToken.value = token
    }

    fun save() {
        if (_areApiKeysHidden.value) return
        viewModelScope.launch {
            prefs.setSourceId(_pendingSourceId.value)
            prefs.setBasePath(_pendingBasePath.value)
            credentialStore.setApiDiscogsKey(_pendingDiscogsKey.value)
            credentialStore.setApiDiscogsSecret(_pendingDiscogsSecret.value)
            credentialStore.setApiLastfmKey(_pendingLastfmKey.value)
            credentialStore.setApiGeniusToken(_pendingGeniusToken.value)
            artistImageLoader.clearCache()
            val sourceId = _pendingSourceId.value
            if (sourceId != null) {
                artistRepository.clearAllArtistImages()
                artistRepository.clearAllArtistBiographies()
            }
        }
    }
}
