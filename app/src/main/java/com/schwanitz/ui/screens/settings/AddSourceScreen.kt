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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.domain.source.SourceType
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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

        if (uiState.isEditing) {
            ConfigureSourceContent(
                selectedType = uiState.selectedType!!,
                selectedProvider = uiState.selectedProvider,
                sourceName = uiState.sourceName,
                folderPathDisplay = uiState.folderPathDisplay,
                url = uiState.url,
                username = uiState.username,
                password = uiState.password,
                path = uiState.path,
                isEditing = true,
                isSaving = uiState.isSaving,
                error = uiState.error,
                connectionTestState = uiState.connectionTestState,
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
            enabled = false,
            onClick = {}
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
    selectedType: SourceType,
    selectedProvider: WebDavProvider?,
    sourceName: String,
    folderPathDisplay: String,
    url: String,
    username: String,
    password: String,
    path: String,
    isSaving: Boolean,
    error: String?,
    connectionTestState: ConnectionTestState,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onSelectProvider: (WebDavProvider?) -> Unit,
    onSelectFolder: () -> Unit,
    onSave: () -> Unit,
    onTestConnection: () -> Unit,
    isEditing: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val usernameFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LaunchedEffect(selectedProvider) {
            if (selectedProvider != null) {
                delay(100.milliseconds)
                usernameFocusRequester.requestFocus()
            }
        }

        OutlinedTextField(
            value = sourceName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.name_label)) },
            placeholder = { Text(stringResource(R.string.add_source_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        when (selectedType) {
            SourceType.LOCAL -> {
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
            SourceType.WEBDAV -> {
                var expanded by remember { mutableStateOf(false) }
                val providerLabel = selectedProvider?.label ?: stringResource(R.string.add_source_provider_custom)
                val urlPlaceholder = selectedProvider?.url ?: ""
                val urlFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { urlFocusRequester.requestFocus() }
                val usernameLabel = selectedProvider?.usernameHint ?: stringResource(R.string.webdav_username_hint)

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
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                        )
                )

                if (selectedProvider != null && selectedProvider.notes.isNotBlank()) {
                    Text(
                        text = selectedProvider.notes,
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
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color(0xFF4CAF50),
                        disabledContentColor = Color.White
                    )
                    is ConnectionTestState.Failure -> ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
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
            }
            SourceType.SMB -> {
                Text(
                    text = stringResource(R.string.add_source_smb_coming_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }


    }
}
