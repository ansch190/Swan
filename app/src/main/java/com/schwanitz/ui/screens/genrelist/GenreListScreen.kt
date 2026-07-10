package com.schwanitz.ui.screens.genrelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreListScreen(
    onNavigateBack: () -> Unit,
    onGenreClick: (String) -> Unit,
    viewModel: GenreListViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadGenres()
    }

    val genres by viewModel.allGenres.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.section_genres)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(genres) { genre ->
                ListItem(
                    modifier = Modifier.clickable { onGenreClick(genre) },
                    headlineContent = { Text(genre) }
                )
            }
        }
    }
}
