package com.schwanitz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanProgress(
    val sourceName: String = "",
    val scanned: Int = 0,
    val total: Int = 0,
    val isScanning: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    val sources: StateFlow<List<SourceConfig>> =
        sourceManager.sources
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            var knownIds = sourceManager.sources.first().map { it.id }.toSet()
            sourceManager.sources.collect { configs ->
                val currentIds = configs.map { it.id }.toSet()
                val newIds = currentIds - knownIds
                for (newId in newIds) {
                    val newConfig = configs.first { it.id == newId }
                    _scanProgress.value = ScanProgress(sourceName = newConfig.name, isScanning = true)
                    musicRepository.refreshSource(newId) { scanned, total ->
                        _scanProgress.value = ScanProgress(sourceName = newConfig.name, scanned = scanned, total = total, isScanning = true)
                    }
                }
                _scanProgress.value = ScanProgress(isScanning = false)
                knownIds = currentIds
            }
        }
    }

    fun toggleSource(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            sourceManager.setSourceEnabled(sourceId, enabled)
            musicRepository.setSourceActive(sourceId, enabled)
        }
    }

    fun deleteSource(sourceId: String) {
        viewModelScope.launch {
            musicRepository.deleteBySource(sourceId)
            sourceManager.removeSource(sourceId)
        }
    }

    fun reloadAll() {
        viewModelScope.launch {
            musicRepository.reloadEnabled { sourceName, scanned, total ->
                _scanProgress.value = ScanProgress(sourceName, scanned, total, isScanning = true)
            }
            _scanProgress.value = ScanProgress(isScanning = false)
        }
    }
}
