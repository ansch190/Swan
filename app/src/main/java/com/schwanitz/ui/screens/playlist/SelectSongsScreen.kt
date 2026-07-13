package com.schwanitz.ui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.schwanitz.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.ui.common.CollectSnackbarErrors
import com.schwanitz.ui.navigation.LocalSnackbarHostState
import com.schwanitz.ui.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSongsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SelectSongsViewModel = hiltViewModel()
) {
    val snackbarHostState = LocalSnackbarHostState.current
    CollectSnackbarErrors(viewModel.errorHolder, snackbarHostState)
    val uiState by viewModel.uiState.collectAsState()
    val selectedIds by viewModel.selectedSongIds.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.select_songs_title)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = {
                IconButton(onClick = { viewModel.toggleFavoritesFilter() }) {
                    Icon(
                        imageVector = if (uiState.showFavoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorites_filter),
                        tint = if (uiState.showFavoritesOnly) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.confirmSelection(onNavigateBack)
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.done),
                        tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) stringResource(R.string.home_empty_search)
                        else stringResource(R.string.home_empty_no_source),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.songs, key = { it.id }) { song ->
                        val isSelected = song.id in selectedIds
                        SongListItem(
                            song = song,
                            onClick = { viewModel.toggleSongSelection(song.id) },
                            onFavoriteClick = { viewModel.toggleSongSelection(song.id) },
                            selected = isSelected
                        )
                    }
                }
            }
        }
    }
}
