package com.schwanitz.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.R
import com.schwanitz.data.backup.BackupOptions
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSources: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel()
) {
    val resources = LocalResources.current
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)

    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.successMessage.collect { message ->
            if (message == BackupViewModel.SUCCESS_IMPORT) {
                onNavigateToSources()
            } else {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.isExporting.collect { isExporting = it }
    }

    val isVerifying by viewModel.isVerifying.collectAsState()
    val importError by viewModel.importError.collectAsState()

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }

    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirm by remember { mutableStateOf("") }
    var exportPasswordError by remember { mutableStateOf<String?>(null) }
    var exportPasswordVisible by remember { mutableStateOf(false) }
    var exportPasswordConfirmVisible by remember { mutableStateOf(false) }
    var hideCredentialsAfterRestore by remember { mutableStateOf(false) }
    var includeLibrary by remember { mutableStateOf(false) }

    var importPassword by remember { mutableStateOf("") }
    var importPasswordVisible by remember { mutableStateOf(false) }
    var importPasswordError by remember { mutableStateOf<String?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var hasAttemptedImport by remember { mutableStateOf(false) }

    LaunchedEffect(isVerifying) {
        if (hasAttemptedImport && !isVerifying) {
            if (importError == null) {
                showImportPasswordDialog = false
                importPassword = ""
                importUri = null
            }
            hasAttemptedImport = false
        }
    }

    LaunchedEffect(importError) {
        if (importError != null) {
            importPasswordError = null
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportTo(
                uri,
                exportPassword,
                BackupOptions(hideCredentialsAfterRestore, includeLibrary)
            )
        }
        exportPassword = ""
        exportPasswordConfirm = ""
        hideCredentialsAfterRestore = false
        includeLibrary = false
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importUri = uri
            showImportPasswordDialog = true
            hasAttemptedImport = false
        }
    }

    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportPasswordDialog = false
                exportPassword = ""
                exportPasswordConfirm = ""
                exportPasswordError = null
                hideCredentialsAfterRestore = false
                includeLibrary = false
            },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = exportPassword,
                        onValueChange = {
                            exportPassword = it
                            exportPasswordError = null
                        },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordVisible = !exportPasswordVisible }) {
                                Icon(
                                    imageVector = if (exportPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportPasswordConfirm,
                        onValueChange = {
                            exportPasswordConfirm = it
                            exportPasswordError = null
                        },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        visualTransformation = if (exportPasswordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { exportPasswordConfirmVisible = !exportPasswordConfirmVisible }) {
                                Icon(
                                    imageVector = if (exportPasswordConfirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hideCredentialsAfterRestore,
                            onCheckedChange = { hideCredentialsAfterRestore = it }
                        )
                        Text(
                            text = stringResource(R.string.backup_hide_credentials_checkbox),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { hideCredentialsAfterRestore = !hideCredentialsAfterRestore }
                        )
                    }
                    Text(
                        text = stringResource(R.string.backup_hide_credentials_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = includeLibrary, onCheckedChange = { includeLibrary = it })
                        Text(
                            text = stringResource(R.string.backup_include_library_checkbox),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clickable { includeLibrary = !includeLibrary }
                        )
                    }
                    if (exportPasswordError != null) {
                        Text(
                            text = exportPasswordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (exportPassword.length < 12) {
                            exportPasswordError = resources.getString(R.string.backup_password_too_short)
                        } else if (exportPassword != exportPasswordConfirm) {
                            exportPasswordError = resources.getString(R.string.backup_password_mismatch)
                        } else {
                            showExportPasswordDialog = false
                            val datePart = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            val suffix = if (includeLibrary) "_library_" else "_"
                            exportLauncher.launch("swan_backup$suffix$datePart.swanbak")
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExportPasswordDialog = false
                    exportPassword = ""
                    exportPasswordConfirm = ""
                    exportPasswordError = null
                    hideCredentialsAfterRestore = false
                    includeLibrary = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportPasswordDialog = false
                importPassword = ""
                importPasswordError = null
                importUri = null
                hasAttemptedImport = false
                viewModel.clearImportError()
            },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = {
                            importPassword = it
                            importPasswordError = null
                            viewModel.clearImportError()
                        },
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        visualTransformation = if (importPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { importPasswordVisible = !importPasswordVisible }) {
                                Icon(
                                    imageVector = if (importPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val displayError = importPasswordError ?: importError
                    if (displayError != null) {
                        Text(
                            text = displayError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importPassword.length < 4) {
                            importPasswordError = resources.getString(R.string.backup_password_too_short)
                        } else if (importUri != null) {
                            hasAttemptedImport = true
                            viewModel.importFrom(importUri!!, importPassword)
                        }
                    },
                    enabled = !isVerifying
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.ok))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportPasswordDialog = false
                        importPassword = ""
                        importPasswordError = null
                        importUri = null
                        hasAttemptedImport = false
                        viewModel.clearImportError()
                    },
                    enabled = !isVerifying
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_backup)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.backup_export_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.backup_export_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { showExportPasswordDialog = true },
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.backup_export_action))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.backup_import_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.backup_import_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = !isVerifying
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.backup_import_action))
                        }
                    }
                }
            }
        }
    }
}
