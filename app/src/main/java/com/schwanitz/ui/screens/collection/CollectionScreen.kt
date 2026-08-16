package com.schwanitz.ui.screens.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schwanitz.R

private data class CollectionTile(
    val titleRes: Int,
    val icon: ImageVector,
    val count: Int,
    val onClick: () -> Unit
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CollectionScreen(
    onAlbumsClick: () -> Unit,
    onAlbumArtistsClick: () -> Unit,
    onGenresClick: () -> Unit,
    onYearsClick: () -> Unit,
    onSeriesClick: () -> Unit,
    viewModel: CollectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tiles = listOf(
        CollectionTile(R.string.section_albums, Icons.Filled.Album, state.albumCount, onAlbumsClick),
        CollectionTile(R.string.section_album_artists, Icons.Filled.People, state.albumArtistCount, onAlbumArtistsClick),
        CollectionTile(R.string.section_genres, Icons.Filled.Category, state.genreCount, onGenresClick),
        CollectionTile(R.string.section_years, Icons.Filled.DateRange, state.yearCount, onYearsClick),
        CollectionTile(R.string.section_series, Icons.Filled.CollectionsBookmark, state.seriesCount, onSeriesClick)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.collection_title)) })
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tiles, key = { it.titleRes }) { tile ->
                CollectionCard(tile = tile, isLoading = state.isLoading)
            }
        }
    }
}

@Composable
private fun CollectionCard(tile: CollectionTile, isLoading: Boolean) {
    val title = stringResource(tile.titleRes)
    val count = if (isLoading) "—" else tile.count.toString()
    val description = stringResource(R.string.collection_tile_description, title, count)

    Card(
        onClick = tile.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = count,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
