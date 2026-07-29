package com.schwanitz.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDataSourceScreen(
    onNavigateBack: () -> Unit,
    viewModel: ArtistDataSourceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var sourceExpanded by remember { mutableStateOf(false) }
    var discogsKeyVisible by remember { mutableStateOf(false) }
    var discogsSecretVisible by remember { mutableStateOf(false) }
    var lastfmKeyVisible by remember { mutableStateOf(false) }
    var geniusTokenVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_artist_data)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = {
                    viewModel.save()
                    onNavigateBack()
                }) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = stringResource(R.string.metadata_api_section_artist_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.artist_data_source_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.artist_data_source_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = !sourceExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    val selectedName = state.pendingSourceId?.let { id ->
                        state.sources.find { it.id == id }?.name
                    }
                    OutlinedTextField(
                        value = selectedName ?: stringResource(R.string.artist_data_disabled),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.artist_data_disabled)) },
                            onClick = {
                                sourceExpanded = false
                                viewModel.updateSourceId(null)
                            }
                        )
                        state.sources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.name) },
                                onClick = {
                                    sourceExpanded = false
                                    viewModel.updateSourceId(source.id)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.artist_data_path_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.artist_data_path_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = state.pendingBasePath,
                    onValueChange = { viewModel.updateBasePath(it) },
                    placeholder = { Text(stringResource(R.string.artist_data_path_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.metadata_api_section_api),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            item {
                val isHidden = state.areApiKeysHidden
                OutlinedTextField(
                    value = state.pendingDiscogsKey,
                    onValueChange = { viewModel.updateDiscogsKey(it) },
                    label = { Text(stringResource(R.string.api_discogs_key_label)) },
                    singleLine = true,
                    enabled = !isHidden,
                    visualTransformation = if (!isHidden && discogsKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (!isHidden) {
                            IconButton(onClick = { discogsKeyVisible = !discogsKeyVisible }) {
                                Icon(
                                    imageVector = if (discogsKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                val isHidden = state.areApiKeysHidden
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pendingDiscogsSecret,
                    onValueChange = { viewModel.updateDiscogsSecret(it) },
                    label = { Text(stringResource(R.string.api_discogs_secret_label)) },
                    singleLine = true,
                    enabled = !isHidden,
                    visualTransformation = if (!isHidden && discogsSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (!isHidden) {
                            IconButton(onClick = { discogsSecretVisible = !discogsSecretVisible }) {
                                Icon(
                                    imageVector = if (discogsSecretVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                val isHidden = state.areApiKeysHidden
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pendingLastfmKey,
                    onValueChange = { viewModel.updateLastfmKey(it) },
                    label = { Text(stringResource(R.string.api_lastfm_key_label)) },
                    singleLine = true,
                    enabled = !isHidden,
                    visualTransformation = if (!isHidden && lastfmKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (!isHidden) {
                            IconButton(onClick = { lastfmKeyVisible = !lastfmKeyVisible }) {
                                Icon(
                                    imageVector = if (lastfmKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                val isHidden = state.areApiKeysHidden
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pendingGeniusToken,
                    onValueChange = { viewModel.updateGeniusToken(it) },
                    label = { Text(stringResource(R.string.api_genius_token_label)) },
                    singleLine = true,
                    enabled = !isHidden,
                    visualTransformation = if (!isHidden && geniusTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        if (!isHidden) {
                            IconButton(onClick = { geniusTokenVisible = !geniusTokenVisible }) {
                                Icon(
                                    imageVector = if (geniusTokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
