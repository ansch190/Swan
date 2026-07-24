package com.schwanitz.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.domain.source.SourceType
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private data class ConfigureSourceState(
    val selectedType: SourceType,
    val selectedProvider: WebDavProvider?,
    val sourceName: String,
    val folderPathDisplay: String,
    val url: String,
    val username: String,
    val password: String,
    val path: String,
    val isSaving: Boolean,
    val error: String?,
    val connectionTestState: ConnectionTestState,
    val isEditing: Boolean = false
)

private data class ConfigureSourceCallbacks(
    val onNameChange: (String) -> Unit,
    val onUrlChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onPathChange: (String) -> Unit,
    val onSelectProvider: (WebDavProvider?) -> Unit,
    val onSelectFolder: () -> Unit,
    val onSave: () -> Unit,
    val onTestConnection: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    sourceId: String? = null,
    onNavigateBack: () -> Unit,
    onSourceAdded: () -> Unit,
    viewModel: AddSourceViewModel = hiltViewModel()
) {
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.selectType(SourceType.LOCAL)
            viewModel.setFolderUri(it)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onSourceAdded()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (uiState.isEditing) stringResource(R.string.add_source_edit_title)
                    else when (uiState.step) {
                        Step.SELECT_TYPE -> stringResource(R.string.add_source_select_type_title)
                        Step.CONFIGURE -> stringResource(R.string.add_source_title)
                    }
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (uiState.step == Step.CONFIGURE && !uiState.isEditing) {
                        viewModel.goToStepSelectType()
                    } else {
                        onNavigateBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                if (uiState.step == Step.CONFIGURE || uiState.isEditing) {
                    val canSave = when (uiState.selectedType) {
                        SourceType.LOCAL -> uiState.sourceName.isNotBlank() && uiState.folderPathDisplay.isNotEmpty()
                        SourceType.WEBDAV -> uiState.sourceName.isNotBlank() && uiState.url.isNotBlank() && uiState.url != "https://" && uiState.username.isNotBlank() && uiState.password.isNotBlank()
                        SourceType.SMB -> uiState.sourceName.isNotBlank() && uiState.url.isNotBlank() && uiState.path.isNotBlank()
                        else -> false
                    }
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = canSave
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.save),
                            tint = if (canSave) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        )

        val callbacks = ConfigureSourceCallbacks(
            onNameChange = { viewModel.updateName(it) },
            onUrlChange = { viewModel.updateUrl(it) },
            onUsernameChange = { viewModel.updateUsername(it) },
            onPasswordChange = { viewModel.updatePassword(it) },
            onPathChange = { viewModel.updatePath(it) },
            onSelectProvider = { viewModel.selectProvider(it) },
            onSelectFolder = { folderPickerLauncher.launch(null) },
            onSave = { viewModel.save() },
            onTestConnection = { viewModel.testConnection() }
        )

        if (uiState.isEditing) {
            ConfigureSourceContent(
                state = ConfigureSourceState(
                    selectedType = uiState.selectedType!!,
                    selectedProvider = uiState.selectedProvider,
                    sourceName = uiState.sourceName,
                    folderPathDisplay = uiState.folderPathDisplay,
                    url = uiState.url,
                    username = uiState.username,
                    password = uiState.password,
                    path = uiState.path,
                    isSaving = uiState.isSaving,
                    error = uiState.error,
                    connectionTestState = uiState.connectionTestState,
                    isEditing = true
                ),
                callbacks = callbacks
            )
        } else when (uiState.step) {
            Step.SELECT_TYPE -> {
                TypeSelectionContent(
                    onTypeSelected = { type ->
                        if (type == SourceType.LOCAL) {
                            folderPickerLauncher.launch(null)
                        } else {
                            viewModel.selectType(type)
                        }
                    }
                )
            }
            Step.CONFIGURE -> {
                ConfigureSourceContent(
                    state = ConfigureSourceState(
                        selectedType = uiState.selectedType!!,
                        selectedProvider = uiState.selectedProvider,
                        sourceName = uiState.sourceName,
                        folderPathDisplay = uiState.folderPathDisplay,
                        url = uiState.url,
                        username = uiState.username,
                        password = uiState.password,
                        path = uiState.path,
                        isSaving = uiState.isSaving,
                        error = uiState.error,
                        connectionTestState = uiState.connectionTestState
                    ),
                    callbacks = callbacks
                )
            }
        }
    }
}

@Composable
private fun TypeSelectionContent(
    onTypeSelected: (SourceType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TypeCard(
            icon = Icons.Filled.Folder,
            title = stringResource(R.string.add_source_local),
            description = stringResource(R.string.add_source_local_desc),
            onClick = { onTypeSelected(SourceType.LOCAL) }
        )

        TypeCard(
            icon = Icons.Filled.Cloud,
            title = stringResource(R.string.add_source_webdav),
            description = stringResource(R.string.add_source_webdav_desc),
            onClick = { onTypeSelected(SourceType.WEBDAV) }
        )

        TypeCard(
            icon = Icons.Filled.DevicesOther,
            title = stringResource(R.string.add_source_smb),
            description = stringResource(R.string.add_source_smb_desc),
            onClick = { onTypeSelected(SourceType.SMB) }
        )
    }
}

@Composable
private fun TypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = if (enabled) CardDefaults.cardColors()
        else CardDefaults.cardColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureSourceContent(
    state: ConfigureSourceState,
    callbacks: ConfigureSourceCallbacks
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.sourceName,
            onValueChange = callbacks.onNameChange,
            label = { Text(stringResource(R.string.name_label)) },
            placeholder = { Text(stringResource(R.string.add_source_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when (state.selectedType) {
            SourceType.LOCAL -> LocalSourceConfig(
                folderPathDisplay = state.folderPathDisplay,
                onSelectFolder = callbacks.onSelectFolder
            )
            SourceType.WEBDAV -> WebDavSourceConfig(
                selectedProvider = state.selectedProvider,
                url = state.url,
                username = state.username,
                password = state.password,
                path = state.path,
                connectionTestState = state.connectionTestState,
                onUrlChange = callbacks.onUrlChange,
                onUsernameChange = callbacks.onUsernameChange,
                onPasswordChange = callbacks.onPasswordChange,
                onPathChange = callbacks.onPathChange,
                onSelectProvider = callbacks.onSelectProvider,
                onTestConnection = callbacks.onTestConnection
            )
            SourceType.SMB -> SmbSourceConfig(
                server = state.url,
                sharePath = state.path,
                username = state.username,
                password = state.password,
                connectionTestState = state.connectionTestState,
                onServerChange = callbacks.onUrlChange,
                onSharePathChange = callbacks.onPathChange,
                onUsernameChange = callbacks.onUsernameChange,
                onPasswordChange = callbacks.onPasswordChange,
                onTestConnection = callbacks.onTestConnection
            )
        }

        if (state.error != null) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun LocalSourceConfig(
    folderPathDisplay: String,
    onSelectFolder: () -> Unit
) {
    OutlinedCard(
        onClick = onSelectFolder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (folderPathDisplay.isNotEmpty())
                    Icons.Filled.CheckCircle else Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = if (folderPathDisplay.isNotEmpty())
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (folderPathDisplay.isNotEmpty()) stringResource(R.string.add_source_folder_selected)
                    else stringResource(R.string.add_source_select_folder),
                    style = MaterialTheme.typography.titleSmall
                )
                if (folderPathDisplay.isNotEmpty()) {
                    Text(
                        text = folderPathDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSourceConfig(
    selectedProvider: WebDavProvider?,
    url: String,
    username: String,
    password: String,
    path: String,
    connectionTestState: ConnectionTestState,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onSelectProvider: (WebDavProvider?) -> Unit,
    onTestConnection: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val usernameFocusRequester = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(false) }
    val providerLabel = selectedProvider?.label ?: stringResource(R.string.add_source_provider_custom)
    val urlPlaceholder = selectedProvider?.url ?: ""
    val urlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider != null) {
            delay(100.milliseconds)
            usernameFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(Unit) { urlFocusRequester.requestFocus() }

    val usernameLabel = selectedProvider?.let { stringResource(it.usernameHintRes) } ?: stringResource(R.string.webdav_username_hint)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = providerLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.add_source_provider_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_source_provider_custom)) },
                onClick = {
                    onSelectProvider(null)
                    expanded = false
                }
            )
            WebDavProvider.PRESETS.forEach { provider ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(provider.label)
                        }
                    },
                    onClick = {
                        onSelectProvider(provider)
                        expanded = false
                    }
                )
            }
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.add_source_server_url_label)) },
        placeholder = { Text(urlPlaceholder) },
        singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(urlFocusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri
            )
    )

    if (selectedProvider != null && selectedProvider.notesRes != 0) {
        Text(
            text = stringResource(selectedProvider.notesRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(usernameLabel) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(usernameFocusRequester),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii
        )
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.add_source_password_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                )
            }
        }
    )
    OutlinedTextField(
        value = path,
        onValueChange = onPathChange,
        label = { Text(stringResource(R.string.add_source_path_label)) },
        placeholder = { Text(stringResource(R.string.add_source_path_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    val buttonEnabled = connectionTestState !is ConnectionTestState.Testing
        && url.isNotBlank() && username.isNotBlank() && password.isNotBlank()
    val buttonColors = when (connectionTestState) {
        is ConnectionTestState.Success -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            disabledContainerColor = Color(0xFF2E7D32),
            disabledContentColor = Color.White
        )
        is ConnectionTestState.Failure -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
        else -> ButtonDefaults.buttonColors()
    }

    Button(
        onClick = onTestConnection,
        enabled = if (connectionTestState is ConnectionTestState.Success) false else buttonEnabled,
        colors = buttonColors,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (val state = connectionTestState) {
            is ConnectionTestState.Testing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_source_testing))
            }
            is ConnectionTestState.Success -> {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_source_connected))
            }
            is ConnectionTestState.Failure -> {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
            else -> Text(stringResource(R.string.add_source_test_connection))
        }
    }

    if (connectionTestState is ConnectionTestState.Failure && connectionTestState.message != null) {
        Text(
            text = connectionTestState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmbSourceConfig(
    server: String,
    sharePath: String,
    username: String,
    password: String,
    connectionTestState: ConnectionTestState,
    onServerChange: (String) -> Unit,
    onSharePathChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val serverFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { serverFocusRequester.requestFocus() }

    OutlinedTextField(
        value = server,
        onValueChange = onServerChange,
        label = { Text(stringResource(R.string.add_source_smb_server)) },
        placeholder = { Text(stringResource(R.string.add_source_smb_server_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(serverFocusRequester),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri
        )
    )

    OutlinedTextField(
        value = sharePath,
        onValueChange = onSharePathChange,
        label = { Text(stringResource(R.string.add_source_smb_share)) },
        placeholder = { Text(stringResource(R.string.add_source_smb_share_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.webdav_username_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii
        )
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.add_source_password_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password
        ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                )
            }
        }
    )

    val buttonEnabled = connectionTestState !is ConnectionTestState.Testing
        && server.isNotBlank() && sharePath.isNotBlank()
    val buttonColors = when (connectionTestState) {
        is ConnectionTestState.Success -> ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2E7D32),
            disabledContainerColor = Color(0xFF2E7D32),
            disabledContentColor = Color.White
        )
        is ConnectionTestState.Failure -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
        else -> ButtonDefaults.buttonColors()
    }

    Button(
        onClick = onTestConnection,
        enabled = if (connectionTestState is ConnectionTestState.Success) false else buttonEnabled,
        colors = buttonColors,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (val state = connectionTestState) {
            is ConnectionTestState.Testing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_source_testing))
            }
            is ConnectionTestState.Success -> {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_source_connected))
            }
            is ConnectionTestState.Failure -> {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.retry))
            }
            else -> Text(stringResource(R.string.add_source_test_connection))
        }
    }

    if (connectionTestState is ConnectionTestState.Failure && connectionTestState.message != null) {
        Text(
            text = connectionTestState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
