package com.schwanitz.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.backup.BackupManager
import com.schwanitz.data.backup.BackupOptions
import com.schwanitz.data.backup.RestoreProgress
import com.schwanitz.data.backup.RestoreStage
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.AEADBadTagException
import javax.inject.Inject

sealed interface RestoreUiState {
    data object Idle : RestoreUiState
    data class AwaitingPassword(val error: RestoreError? = null) : RestoreUiState
    data class Running(val progress: RestoreProgress) : RestoreUiState
    data class Failed(val error: RestoreError) : RestoreUiState
}

enum class RestoreError {
    WRONG_PASSWORD,
    IMPORT_FAILED
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager
) : ViewModel() {

    val errorHolder = ErrorHolder()

    private val _successMessage = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val successMessage: SharedFlow<String> = _successMessage.asSharedFlow()

    private val _isExporting = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val isExporting: SharedFlow<Boolean> = _isExporting.asSharedFlow()

    private val _restoreState = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val restoreState: StateFlow<RestoreUiState> = _restoreState.asStateFlow()
    private var pendingImportUri: Uri? = null

    fun exportTo(uri: Uri, password: String, options: BackupOptions = BackupOptions()) {
        viewModelScope.launch {
            _isExporting.tryEmit(true)
            try {
                val backup = backupManager.createBackup(options)
                backupManager.exportTo(context.contentResolver, uri, backup, password)
                _successMessage.tryEmit(SUCCESS_EXPORT)
            } catch (e: Exception) {
                errorHolder.emit(e, ERROR_EXPORT)
            } finally {
                _isExporting.tryEmit(false)
            }
        }
    }

    fun selectImport(uri: Uri) {
        if (_restoreState.value is RestoreUiState.Running) return
        pendingImportUri = uri
        _restoreState.value = RestoreUiState.AwaitingPassword()
    }

    fun startImport(password: String) {
        if (_restoreState.value !is RestoreUiState.AwaitingPassword) return
        val uri = pendingImportUri ?: return
        _restoreState.value = RestoreUiState.Running(RestoreProgress(RestoreStage.PREPARING_KEY))
        viewModelScope.launch {
            try {
                backupManager.importAndRestore(context.contentResolver, uri, password) { progress ->
                    _restoreState.value = RestoreUiState.Running(progress)
                }
                pendingImportUri = null
                _restoreState.value = RestoreUiState.Idle
                _successMessage.tryEmit(SUCCESS_IMPORT)
            } catch (e: AEADBadTagException) {
                _restoreState.value = RestoreUiState.AwaitingPassword(RestoreError.WRONG_PASSWORD)
            } catch (e: Exception) {
                _restoreState.value = RestoreUiState.Failed(RestoreError.IMPORT_FAILED)
            }
        }
    }

    fun dismissPasswordRequest() {
        if (_restoreState.value !is RestoreUiState.AwaitingPassword) return
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
        pendingImportUri = null
        _restoreState.value = RestoreUiState.Idle
    }

    companion object {
        const val SUCCESS_EXPORT = "Backup erfolgreich gespeichert"
        const val SUCCESS_IMPORT = "Backup erfolgreich wiederhergestellt"
        const val ERROR_EXPORT = "Backup fehlgeschlagen"
    }
}
