package com.schwanitz.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.source.WebDavMusicSource
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddSourceUiState(
    val step: Step = Step.SELECT_TYPE,
    val selectedType: SourceType? = null,
    val selectedProvider: WebDavProvider? = null,
    val sourceName: String = "",
    val folderUri: Uri? = null,
    val folderPathDisplay: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "/",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val connectionTestState: ConnectionTestState = ConnectionTestState.Idle
)

enum class Step { SELECT_TYPE, CONFIGURE }

sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Failure(val error: String) : ConnectionTestState()
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val sourceManager: SourceManager,
    private val webDavMusicSource: WebDavMusicSource,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editSourceId: String? = savedStateHandle.get<String>("sourceId")

    private val _uiState = MutableStateFlow(AddSourceUiState())
    val uiState: StateFlow<AddSourceUiState> = _uiState.asStateFlow()

    init {
        if (editSourceId != null) {
            viewModelScope.launch {
                val source = sourceManager.sources.first().find { it.id == editSourceId }
                if (source != null) {
                    _uiState.value = AddSourceUiState(
                        step = Step.CONFIGURE,
                        selectedType = source.type,
                        sourceName = source.name,
                        folderUri = source.folderUri?.let { Uri.parse(it) },
                        folderPathDisplay = source.folderUri?.let {
                            Uri.decode(it.substringAfterLast('/')).substringAfter(':')
                        } ?: "",
                        url = source.url ?: "",
                        username = source.username ?: "",
                        password = source.password ?: "",
                        path = source.path ?: "/",
                        isEditing = true
                    )
                }
            }
        }
    }

    fun goToStepSelectType() {
        _uiState.value = AddSourceUiState()
    }

    fun selectType(type: SourceType) {
        _uiState.value = _uiState.value.copy(step = Step.CONFIGURE, selectedType = type, error = null)
    }

    fun selectProvider(provider: WebDavProvider?) {
        if (provider == null) {
            _uiState.value = _uiState.value.copy(selectedProvider = null, error = null, connectionTestState = ConnectionTestState.Idle)
            return
        }
        val newName = if (_uiState.value.sourceName.isBlank()) provider.label else _uiState.value.sourceName
        _uiState.value = _uiState.value.copy(
            selectedProvider = provider,
            sourceName = newName,
            url = provider.url,
            path = provider.path,
            error = null,
            connectionTestState = ConnectionTestState.Idle
        )
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(sourceName = name, error = null)
    }

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url, error = null, connectionTestState = ConnectionTestState.Idle)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null, connectionTestState = ConnectionTestState.Idle)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null, connectionTestState = ConnectionTestState.Idle)
    }

    fun updatePath(path: String) {
        _uiState.value = _uiState.value.copy(path = path, error = null, connectionTestState = ConnectionTestState.Idle)
    }

    fun setFolderUri(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            folderUri = uri,
            folderPathDisplay = Uri.decode(uri.toString().substringAfterLast('/')).substringAfter(':'),
            sourceName = extractFolderName(uri),
            error = null
        )
    }

    fun testConnection() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = state.copy(connectionTestState = ConnectionTestState.Testing)
            val result = webDavMusicSource.testConnection(
                url = state.url,
                username = state.username,
                password = state.password,
                path = state.path
            )
            _uiState.value = _uiState.value.copy(
                connectionTestState = result.fold(
                    onSuccess = { ConnectionTestState.Success(it) },
                    onFailure = { ConnectionTestState.Failure(it.message ?: "Unknown error") }
                )
            )
        }
    }

    private fun extractFolderName(uri: Uri): String {
        val path = uri.path ?: return ""
        val decoded = Uri.decode(path)
        val afterColon = decoded.substringAfterLast(':')
        val lastSegment = afterColon.substringAfterLast('/')
        return lastSegment.ifBlank { "Untitled" }
    }

    fun save() {
        val state = _uiState.value
        val type = state.selectedType ?: return
        val name = state.sourceName.ifBlank { "Untitled" }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val existing = sourceManager.sources.first()
            when (type) {
                SourceType.LOCAL -> {
                    if (state.folderUri != null && existing.any { it.id != editSourceId && it.folderUri == state.folderUri.toString() }) {
                        _uiState.value = _uiState.value.copy(isSaving = false, error = "This source already exists")
                        return@launch
                    }
                }
                SourceType.WEBDAV -> {
                    val cleanUrl = state.url.trimEnd('/')
                    if (cleanUrl.isBlank()) {
                        _uiState.value = _uiState.value.copy(isSaving = false, error = "URL required")
                        return@launch
                    }
                    if (existing.any { it.id != editSourceId && it.url?.trimEnd('/') == cleanUrl }) {
                        _uiState.value = _uiState.value.copy(isSaving = false, error = "This URL already exists")
                        return@launch
                    }
                }
                SourceType.SMB -> {}
            }

            val config = SourceConfig(
                id = editSourceId ?: UUID.randomUUID().toString(),
                name = name,
                type = type,
                isEnabled = true,
                folderUri = state.folderUri?.toString(),
                url = state.url.trimEnd('/').takeIf { it.isNotBlank() },
                username = state.username.takeIf { it.isNotBlank() },
                password = state.password.takeIf { it.isNotBlank() },
                path = state.path.trim('/').takeIf { it.isNotBlank() }
            )

            if (state.isEditing) {
                sourceManager.updateSource(config)
            } else {
                sourceManager.addSource(config)
            }

            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
        }
    }
}
