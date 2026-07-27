package com.schwanitz.ui.screens.yearlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.schwanitz.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearListScreen(
    onNavigateBack: () -> Unit,
    onYearClick: (Int) -> Unit,
    onDecadeClick: (Int) -> Unit,
    viewModel: YearListViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadYears()
    }

    val years by viewModel.allYears.collectAsState()

    val yearsByDecade = remember(years) {
        years.groupBy { it / 10 }
            .toSortedMap(compareByDescending { it })
    }

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
            yearsByDecade.forEach { (decade, decadeYears) ->
                stickyHeader {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDecadeClick(decade * 10) },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = stringResource(R.string.decade_format, decade * 10),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }
                items(decadeYears) { year ->
                    ListItem(
                        modifier = Modifier.clickable { onYearClick(year) },
                        headlineContent = {
                            Text(
                                text = year.toString(),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }
        }
    }
}
