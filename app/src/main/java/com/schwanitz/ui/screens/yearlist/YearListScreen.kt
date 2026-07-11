package com.schwanitz.ui.screens.yearlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearListScreen(
    onNavigateBack: () -> Unit,
    onYearClick: (Int) -> Unit,
    viewModel: YearListViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadYears()
    }

    val years by viewModel.allYears.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.section_years)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(years) { year ->
                ListItem(
                    modifier = Modifier.clickable { onYearClick(year) },
                    headlineContent = { Text(year.toString()) }
                )
            }
        }
    }
}
