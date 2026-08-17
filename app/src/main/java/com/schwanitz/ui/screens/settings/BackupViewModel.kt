package com.schwanitz.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.schwanitz.data.backup.BackupJobCoordinator
import com.schwanitz.data.backup.BackupOperation
import com.schwanitz.data.backup.BackupOptions
import com.schwanitz.data.backup.BackupUriPermissionException
import com.schwanitz.data.backup.BackupWorkState
import com.schwanitz.data.backup.BackupWorker
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RestoreUiState {
    data object Idle : RestoreUiState
    data class AwaitingPassword(val error: RestoreError? = null) : RestoreUiState
    data class Failed(val error: RestoreError) : RestoreUiState
}

enum class RestoreError { WRONG_PASSWORD, IMPORT_FAILED, URI_PERMISSION }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val coordinator: BackupJobCoordinator,
) : ViewModel() {

    val errorHolder = ErrorHolder()
    private val successEvents = Channel<String>(Channel.BUFFERED)
    val successMessage: Flow<String> = successEvents.receiveAsFlow()
    private val _workState = MutableStateFlow<BackupWorkState?>(null)
    val workState: StateFlow<BackupWorkState?> = _workState.asStateFlow()
    private val _restoreState = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val restoreState: StateFlow<RestoreUiState> = _restoreState.asStateFlow()
    private var pendingImportUri: Uri? = null

    init {
        viewModelScope.launch {
            coordinator.workState.collect { state ->
                if (state == null) _workState.value = null
                else if (state.state.isFinished) handleFinished(state)
                else _workState.value = state
            }
        }
    }

    fun exportTo(uri: Uri, password: String, options: BackupOptions = BackupOptions()) {
        if (_workState.value?.isRunning == true) return
        viewModelScope.launch {
            runCatching { coordinator.enqueueExport(uri, password, options) }
                .onFailure { errorHolder.emit(it, ERROR_EXPORT) }
        }
    }

    fun selectImport(uri: Uri) {
        if (_workState.value?.isRunning == true) return
        pendingImportUri = uri
        _restoreState.value = RestoreUiState.AwaitingPassword()
    }

    fun startImport(password: String) {
        if (_restoreState.value !is RestoreUiState.AwaitingPassword) return
        val uri = pendingImportUri ?: return
        _restoreState.value = RestoreUiState.Idle
        viewModelScope.launch {
            runCatching { coordinator.enqueueRestore(uri, password) }
                .onFailure { error ->
                    _restoreState.value = if (error is BackupUriPermissionException) {
                        RestoreUiState.Failed(RestoreError.URI_PERMISSION)
                    } else RestoreUiState.Failed(RestoreError.IMPORT_FAILED)
                }
        }
    }

    fun cancelExport() {
        _workState.value?.takeIf {
            it.operation == BackupOperation.EXPORT && it.isRunning
        }?.let { coordinator.cancelExport(it.workId) }
    }

    fun dismissPasswordRequest() {
        if (_restoreState.value !is RestoreUiState.AwaitingPassword) return
        pendingImportUri?.let { coordinator.releasePermission(it, BackupOperation.RESTORE) }
        pendingImportUri = null
        _restoreState.value = RestoreUiState.Idle
    }

    fun clearPasswordError() {
        val state = _restoreState.value
        if (state is RestoreUiState.AwaitingPassword && state.error != null) {
            _restoreState.value = RestoreUiState.AwaitingPassword()
        }
    }

    fun retryImport() {
        if (_restoreState.value is RestoreUiState.Failed && pendingImportUri != null) {
            _restoreState.value = RestoreUiState.AwaitingPassword()
        }
    }

    fun dismissImportFailure() {
        if (_restoreState.value !is RestoreUiState.Failed) return
        pendingImportUri?.let { coordinator.releasePermission(it, BackupOperation.RESTORE) }
        pendingImportUri = null
        _restoreState.value = RestoreUiState.Idle
    }

    private fun handleFinished(state: BackupWorkState) {
        _workState.value = null
        when {
            state.state == WorkInfo.State.SUCCEEDED -> {
                coordinator.acknowledge(state)
                if (state.operation == BackupOperation.EXPORT) successEvents.trySend(SUCCESS_EXPORT)
                else {
                    pendingImportUri = null
                    _restoreState.value = RestoreUiState.Idle
                    successEvents.trySend(SUCCESS_IMPORT)
                }
            }
            state.operation == BackupOperation.RESTORE && state.error == BackupWorker.ERROR_WRONG_PASSWORD -> {
                pendingImportUri = state.uri
                coordinator.acknowledge(state, releasePermission = false)
                _restoreState.value = RestoreUiState.AwaitingPassword(RestoreError.WRONG_PASSWORD)
            }
            state.operation == BackupOperation.RESTORE -> {
                pendingImportUri = state.uri
                coordinator.acknowledge(state, releasePermission = false)
                _restoreState.value = RestoreUiState.Failed(RestoreError.IMPORT_FAILED)
            }
            state.state == WorkInfo.State.CANCELLED -> coordinator.acknowledge(state)
            else -> {
                coordinator.acknowledge(state)
                errorHolder.emit(IllegalStateException("Backup export failed"), ERROR_EXPORT)
            }
        }
    }

    companion object {
        const val SUCCESS_EXPORT = "Backup erfolgreich gespeichert"
        const val SUCCESS_IMPORT = "Backup erfolgreich wiederhergestellt"
        const val ERROR_EXPORT = "Backup fehlgeschlagen"
    }
}
