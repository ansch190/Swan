package com.schwanitz.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.schwanitz.data.local.SharedImportPreferences
import com.schwanitz.data.source.SourceScanCoordinator
import com.schwanitz.domain.repository.SourceLifecycleManager
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
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
    private val sourceScanCoordinator: SourceScanCoordinator,
    sharedImportPreferences: SharedImportPreferences
) : ViewModel() {

    private val _scanFeedback = MutableSharedFlow<ScanFeedback>(extraBufferCapacity = 8)
    val scanFeedback: SharedFlow<ScanFeedback> = _scanFeedback.asSharedFlow()

    val errorHolder = ErrorHolder()

    val sources: StateFlow<List<SourceConfig>> =
        sourceManager.sources
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scanProgress: StateFlow<ScanProgress> = combine(
        sourceScanCoordinator.workStates,
        sources,
    ) { workStates, configs ->
        val active = workStates.firstOrNull { it.state == WorkInfo.State.RUNNING }
            ?: workStates.firstOrNull {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
            }
        if (active == null) {
            ScanProgress()
        } else {
            ScanProgress(
                sourceName = active.sourceName.ifBlank {
                    configs.firstOrNull { it.id == active.sourceId }?.name ?: active.sourceId
                },
                scanned = active.scanned,
                total = active.total,
                isScanning = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScanProgress())

    val localSourcesRequiringAuthorization: StateFlow<Set<String>> =
        sharedImportPreferences.localSourcesRequiringAuthorization
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            val handledTerminalWork = mutableSetOf<String>()
            var initialSnapshot = true
            sourceScanCoordinator.workStates.collect { states ->
                if (initialSnapshot) {
                    handledTerminalWork += states.filter { it.state.isFinished }.map { it.workId }
                    initialSnapshot = false
                    return@collect
                }
                states.filter { it.state.isFinished && handledTerminalWork.add(it.workId) }
                    .forEach { state ->
                        val sourceName = state.sourceName.ifBlank {
                            sources.value.firstOrNull { it.id == state.sourceId }?.name ?: state.sourceId
                        }
                        when (state.state) {
                            WorkInfo.State.SUCCEEDED -> _scanFeedback.emit(
                                if (state.retainedFailures > 0) {
                                    ScanFeedback.CompletedWithWarnings(
                                        sourceName,
                                        state.total,
                                        state.retainedFailures,
                                    )
                                } else {
                                    ScanFeedback.Completed(sourceName, state.total)
                                }
                            )

                            WorkInfo.State.FAILED -> {
                                _scanFeedback.emit(ScanFeedback.FailedWithRetainedLibrary(sourceName))
                                state.error?.let { errorHolder.emit(IllegalStateException(it)) }
                            }

                            else -> Unit
                        }
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
        sourceScanCoordinator.cancel(sourceId)
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
            runCatching { sourceScanCoordinator.enqueueEnabled() }
                .exceptionOrNull()
                ?.let { errorHolder.emit(it) }
        }
    }
}
