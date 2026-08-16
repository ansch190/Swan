package com.schwanitz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.repository.SourceLifecycleManager
import com.schwanitz.data.local.SharedImportPreferences
import com.schwanitz.domain.repository.SourceRefreshResult
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ScanProgress(
    val sourceName: String = "",
    val scanned: Int = 0,
    val total: Int = 0,
    val isScanning: Boolean = false
)

sealed interface ScanFeedback {
    data class Completed(val sourceName: String, val total: Int) : ScanFeedback
    data class CompletedWithWarnings(val sourceName: String, val total: Int, val retainedFailures: Int) : ScanFeedback
    data class FailedWithRetainedLibrary(val sourceName: String) : ScanFeedback
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val sourceLifecycleManager: SourceLifecycleManager,
    sharedImportPreferences: SharedImportPreferences
) : ViewModel() {

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()
    private val _scanFeedback = MutableSharedFlow<ScanFeedback>(extraBufferCapacity = 8)
    val scanFeedback: SharedFlow<ScanFeedback> = _scanFeedback.asSharedFlow()

    val errorHolder = ErrorHolder()

    val sources: StateFlow<List<SourceConfig>> =
        sourceManager.sources
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localSourcesRequiringAuthorization: StateFlow<Set<String>> =
        sharedImportPreferences.localSourcesRequiringAuthorization
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            var knownIds = sourceManager.sources.first().map { it.id }.toSet()
            sourceManager.sources.collect { configs ->
                try {
                    val currentIds = configs.map { it.id }.toSet()
                    val newIds = currentIds - knownIds
                    for (newId in newIds) {
                        val newConfig = configs.first { it.id == newId }
                        Timber.i("New source detected: '%s', starting scan", newConfig.name)
                        _scanProgress.value = ScanProgress(sourceName = newConfig.name, isScanning = true)
                        val result = sourceLifecycleManager.refreshSource(newId) { scanned, total ->
                            _scanProgress.value = ScanProgress(sourceName = newConfig.name, scanned = scanned, total = total, isScanning = true)
                        }
                        publishResult(newConfig.name, result)
                    }
                    _scanProgress.value = ScanProgress(isScanning = false)
                    knownIds = currentIds
                } catch (e: Exception) {
                    errorHolder.emit(e)
                } finally {
                    _scanProgress.value = ScanProgress()
                }
            }
        }
    }

    fun toggleSource(sourceId: String, enabled: Boolean) {
        Timber.d("Toggling source %s: %s", sourceId, if (enabled) "enabled" else "disabled")
        viewModelScope.launch {
            runCatching {
                sourceManager.setSourceEnabled(sourceId, enabled)
                sourceLifecycleManager.setSourceActive(sourceId, enabled)
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun deleteSource(sourceId: String) {
        Timber.i("Deleting source %s", sourceId)
        viewModelScope.launch {
            runCatching {
                sourceLifecycleManager.deleteBySource(sourceId)
                sourceManager.removeSource(sourceId)
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun reloadAll() {
        Timber.i("Reloading all enabled sources")
        viewModelScope.launch {
            try {
                val results = sourceLifecycleManager.reloadEnabled { sourceName, scanned, total ->
                    _scanProgress.value = ScanProgress(sourceName, scanned, total, isScanning = true)
                }
                val names = sources.value.associate { it.id to it.name }
                results.forEach { (id, result) -> publishResult(names[id] ?: id, result) }
            } catch (e: Exception) {
                errorHolder.emit(e)
            } finally {
                _scanProgress.value = ScanProgress()
            }
        }
    }

    private fun publishResult(sourceName: String, result: SourceRefreshResult) {
        when (result) {
            is SourceRefreshResult.Success -> _scanFeedback.tryEmit(
                if (result.retainedFailures > 0) {
                    ScanFeedback.CompletedWithWarnings(sourceName, result.total, result.retainedFailures)
                } else {
                    ScanFeedback.Completed(sourceName, result.total)
                }
            )
            is SourceRefreshResult.Failure -> {
                _scanFeedback.tryEmit(ScanFeedback.FailedWithRetainedLibrary(sourceName))
                errorHolder.emit(result.error)
            }
        }
    }
}
