package com.schwanitz.ui.screens.albumlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.schwanitz.ui.components.AlbumListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumListScreen(
    onNavigateBack: () -> Unit,
    onAlbumClick: (String, String, Int) -> Unit,
    viewModel: AlbumListViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadAlbums()
    }

    val albums by viewModel.allAlbums.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.section_albums)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(albums) { album ->
                AlbumListItem(
                    albumName = album.name,
                    albumArtUri = album.albumArtUri,
                    year = album.year,
                    albumArtist = album.albumArtist,
                    onClick = { onAlbumClick(album.name, album.albumArtist, album.year) }
                )
            }
        }
    }
}
