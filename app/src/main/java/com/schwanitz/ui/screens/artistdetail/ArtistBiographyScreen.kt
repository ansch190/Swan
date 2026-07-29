package com.schwanitz.ui.screens.artistdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.schwanitz.R
import com.schwanitz.domain.model.BioSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistBiographyScreen(
    artistName: String,
    onNavigateBack: () -> Unit,
    viewModel: ArtistDetailViewModel
) {
    val biographyResult by viewModel.artistBiography.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.artist_biography_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val result = biographyResult
            if (result != null) {
                Markdown(
                    content = result.text,
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.headlineMedium,
                        h2 = MaterialTheme.typography.headlineSmall,
                        h3 = MaterialTheme.typography.titleLarge,
                    )
                )
                if (result.source == BioSource.LASTFM) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.biography_source_lastfm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(stringResource(R.string.biography_not_available))
            }
        }
    }
}
