package com.schwanitz.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.backup.BackupManager
import com.schwanitz.data.backup.BackupOptions
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

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

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

    fun importFrom(uri: Uri, password: String) {
        viewModelScope.launch {
            _isVerifying.value = true
            _importError.value = null
            try {
                backupManager.importAndRestore(context.contentResolver, uri, password)
                _successMessage.tryEmit(SUCCESS_IMPORT)
            } catch (e: AEADBadTagException) {
                _importError.value = ERROR_WRONG_PASSWORD
            } catch (e: Exception) {
                _importError.value = ERROR_IMPORT
            } finally {
                _isVerifying.value = false
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }

    companion object {
        const val SUCCESS_EXPORT = "Backup erfolgreich gespeichert"
        const val SUCCESS_IMPORT = "Backup erfolgreich wiederhergestellt"
        const val ERROR_EXPORT = "Backup fehlgeschlagen"
        const val ERROR_IMPORT = "Wiederherstellung fehlgeschlagen"
        const val ERROR_WRONG_PASSWORD = "Falsches Passwort"
    }
}
