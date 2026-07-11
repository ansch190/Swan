package com.schwanitz.ui.screens.serieslist

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
fun SeriesListScreen(
    onNavigateBack: () -> Unit,
    onSeriesClick: (String) -> Unit,
    viewModel: SeriesListViewModel = hiltViewModel()
) {
    val series by viewModel.allSeries.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.section_series)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(series) { s ->
                ListItem(
                    modifier = Modifier.clickable { onSeriesClick(s.name) },
                    headlineContent = { Text(s.name.ifBlank { "-" }) }
                )
            }
        }
    }
}
