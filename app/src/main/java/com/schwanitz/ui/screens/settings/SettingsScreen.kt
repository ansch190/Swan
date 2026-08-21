package com.schwanitz.ui.screens.settings

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState
import com.schwanitz.domain.source.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onAddSource: () -> Unit,
    onEditSource: (sourceId: String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = LocalSnackbarHostState.current
    val resources = LocalResources.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val localSourcesRequiringAuthorization by viewModel.localSourcesRequiringAuthorization.collectAsStateWithLifecycle()
    var sourceToDelete by remember { mutableStateOf<SourceConfig?>(null) }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.scanFeedback.collect { feedback ->
            val message = when (feedback) {
                is ScanFeedback.Completed -> resources.getString(R.string.settings_scan_success, feedback.sourceName, feedback.total)
                is ScanFeedback.CompletedWithWarnings -> resources.getString(
                    R.string.settings_scan_success_warnings,
                    feedback.sourceName,
                    feedback.total,
                    feedback.retainedFailures
                )
                is ScanFeedback.FailedWithRetainedLibrary -> resources.getString(
                    R.string.settings_scan_failed_retained,
                    feedback.sourceName
                )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    val localSources = sources.filter { it.type == SourceType.LOCAL }
    val networkSources = sources.filter { it.type != SourceType.LOCAL }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_sources_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = onAddSource) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_source))
                }
            }
        )

        if (scanProgress.isScanning) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (scanProgress.total > 0) {
                    LinearProgressIndicator(
                        progress = { scanProgress.scanned.toFloat() / scanProgress.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (scanProgress.total > 0) {
                        stringResource(R.string.settings_scanning, scanProgress.sourceName, scanProgress.scanned, scanProgress.total)
                    } else {
                        stringResource(R.string.settings_scanning_unknown, scanProgress.sourceName, scanProgress.scanned)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = viewModel::cancelCurrentScan,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.settings_cancel_scan))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            if (localSources.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.settings_local_section)) }
                items(localSources, key = { it.id }) { source ->
                    SourceItem(
                        source = source,
                        requiresLocalAuthorization = source.id in localSourcesRequiringAuthorization,
                        onEditClick = { onEditSource(source.id) },
                        onToggle = { enabled -> viewModel.toggleSource(source.id, enabled) },
                        onDelete = { sourceToDelete = source }
                    )
                }
            }

            if (networkSources.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.settings_network_section)) }
                items(networkSources, key = { it.id }) { source ->
                    SourceItem(
                        source = source,
                        onEditClick = { onEditSource(source.id) },
                        onToggle = { enabled -> viewModel.toggleSource(source.id, enabled) },
                        onDelete = { sourceToDelete = source }
                    )
                }
            }

            if (sources.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.reloadAll() },
                        enabled = !scanProgress.isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_reload_all))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    sourceToDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceToDelete = null },
            title = { Text(stringResource(R.string.settings_delete_source_title)) },
            text = { Text(stringResource(R.string.settings_delete_source_message, source.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSource(source.id)
                        sourceToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sourceToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceItem(
    source: SourceConfig,
    requiresLocalAuthorization: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (source.type) {
        SourceType.LOCAL -> Icons.Filled.Folder
        SourceType.WEBDAV -> Icons.Filled.Cloud
        SourceType.SMB -> Icons.Filled.DevicesOther
    }

    val subtitle = when (source.type) {
        SourceType.LOCAL -> {
            if (requiresLocalAuthorization) {
                stringResource(R.string.settings_source_reauthorize)
            } else {
                val raw = source.folderUri?.substringAfterLast('/')
                if (raw != null) Uri.decode(raw).substringAfter(':') else stringResource(R.string.settings_source_local)
            }
        }
        SourceType.WEBDAV -> source.url ?: stringResource(R.string.settings_source_webdav)
        SourceType.SMB -> {
            val host = source.url ?: ""
            val share = source.path ?: ""
            if (host.isNotEmpty()) "$host/$share" else stringResource(R.string.settings_source_smb)
        }
    }

    val modifier = if (onEditClick != null) {
        Modifier.clickable(onClick = onEditClick)
    } else {
        Modifier
    }

    ListItem(
        headlineContent = { Text(source.name) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onEditClick != null) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_edit_source),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(text = subtitle, maxLines = 1)
                }
            }
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.cd_delete_source),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = source.isEnabled,
                    onCheckedChange = onToggle
                )
            }
        },
        modifier = modifier
    )
}
