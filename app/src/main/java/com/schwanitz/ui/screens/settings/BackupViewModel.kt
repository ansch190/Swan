package com.schwanitz.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.backup.BackupManager
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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

    private val _isImporting = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val isImporting: SharedFlow<Boolean> = _isImporting.asSharedFlow()

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _isExporting.tryEmit(true)
            try {
                val backup = backupManager.createBackup()
                backupManager.exportTo(context.contentResolver, uri, backup)
                _successMessage.tryEmit(SUCCESS_EXPORT)
            } catch (e: Exception) {
                errorHolder.emit(e, ERROR_EXPORT)
            } finally {
                _isExporting.tryEmit(false)
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _isImporting.tryEmit(true)
            try {
                val backup = backupManager.importFrom(context.contentResolver, uri)
                backupManager.restore(backup)
                _successMessage.tryEmit(SUCCESS_IMPORT)
            } catch (e: Exception) {
                errorHolder.emit(e, ERROR_IMPORT)
            } finally {
                _isImporting.tryEmit(false)
            }
        }
    }

    companion object {
        const val SUCCESS_EXPORT = "Backup erfolgreich gespeichert"
        const val SUCCESS_IMPORT = "Backup erfolgreich wiederhergestellt"
        const val ERROR_EXPORT = "Backup fehlgeschlagen"
        const val ERROR_IMPORT = "Wiederherstellung fehlgeschlagen"
    }
}
