package com.schwanitz.ui.screens.settings

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.R
import com.schwanitz.data.backup.BackupOptions
import com.schwanitz.data.backup.BackupOperation
import com.schwanitz.data.backup.BackupJobProgress
import com.schwanitz.data.backup.BackupJobStage
import com.schwanitz.data.backup.stringRes
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

    LaunchedEffect(Unit) {
        viewModel.successMessage.collect { message ->
            if (message == BackupViewModel.SUCCESS_IMPORT) {
                onNavigateToSources()
            } else {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val restoreState by viewModel.restoreState.collectAsState()
    val workState by viewModel.workState.collectAsState()
    val isExporting = workState?.operation == BackupOperation.EXPORT && workState?.isRunning == true
    val isRestoring = workState?.operation == BackupOperation.RESTORE && workState?.isRunning == true
    val isWorking = isExporting || isRestoring

    var showExportPasswordDialog by remember { mutableStateOf(false) }

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
            viewModel.selectImport(uri)
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

    val passwordState = restoreState as? RestoreUiState.AwaitingPassword
    if (passwordState != null) {
        AlertDialog(
            onDismissRequest = {
                importPassword = ""
                importPasswordError = null
                viewModel.dismissPasswordRequest()
            },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = {
                            importPassword = it
                            importPasswordError = null
                            viewModel.clearPasswordError()
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
                    val displayError = importPasswordError ?: when (passwordState.error) {
                        RestoreError.WRONG_PASSWORD -> stringResource(R.string.backup_restore_wrong_password)
                        else -> null
                    }
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
                            importPasswordError = resources.getString(R.string.backup_import_password_too_short)
                        } else {
                            val password = importPassword
                            importPassword = ""
                            importPasswordVisible = false
                            viewModel.startImport(password)
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        importPassword = ""
                        importPasswordError = null
                        viewModel.dismissPasswordRequest()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (restoreState is RestoreUiState.Failed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restore_failed_title)) },
            text = {
                Text(
                    stringResource(
                        if ((restoreState as RestoreUiState.Failed).error == RestoreError.URI_PERMISSION) {
                            R.string.backup_uri_permission_failed
                        } else R.string.backup_restore_failed_message
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::retryImport) {
                    Text(stringResource(R.string.retry))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImportFailure) {
                    Text(stringResource(R.string.about_close))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_backup)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack, enabled = !isRestoring) {
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
            if (isExporting && workState?.progress != null) {
                BackupProgressCard(
                    progress = workState!!.progress!!,
                    onCancel = viewModel::cancelExport,
                )
            }
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
                        enabled = !isWorking
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
                        enabled = !isWorking
                    ) {
                        Text(stringResource(R.string.backup_import_action))
                    }
                }
            }
        }
    }
}

@Composable
fun BackupProgressDetails(progress: BackupJobProgress) {
    val context = LocalContext.current
    val stage = stringResource(
        progress.stage.stringRes()
    )
    val byteProgress = progress.totalBytes?.takeIf { it > 0 }?.let { total ->
        stringResource(
            R.string.backup_restore_bytes_progress,
            Formatter.formatShortFileSize(context, progress.completedBytes),
            Formatter.formatShortFileSize(context, total)
        )
    }
    val itemProgress = if (progress.completedItems != null && progress.totalItems != null) {
        val resource = when (progress.stage) {
            BackupJobStage.VALIDATING -> R.string.backup_restore_entries_progress
            BackupJobStage.EXTRACTING_ASSETS,
            BackupJobStage.EXPORTING_ASSETS -> R.string.backup_restore_files_progress
            BackupJobStage.RESTORING_LIBRARY,
            BackupJobStage.EXPORTING_LIBRARY -> R.string.backup_restore_tables_progress
            else -> null
        }
        resource?.let { stringResource(it, progress.completedItems, progress.totalItems) }
    } else null
    val stateText = listOfNotNull(stage, byteProgress, itemProgress).joinToString(". ")

    Column(
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = stateText
                },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stage, style = MaterialTheme.typography.titleSmall)
                val fraction = progress.fraction
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (byteProgress != null) Text(byteProgress, style = MaterialTheme.typography.bodyMedium)
                if (itemProgress != null) Text(itemProgress, style = MaterialTheme.typography.bodyMedium)
            }
}

@Composable
private fun BackupProgressCard(
    progress: BackupJobProgress,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.backup_export_progress_title), style = MaterialTheme.typography.titleMedium)
            BackupProgressDetails(progress)
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
