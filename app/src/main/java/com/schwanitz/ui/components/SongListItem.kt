package com.schwanitz.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.schwanitz.R
import com.schwanitz.domain.model.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showAlbumInSubtitle: Boolean = true
) {
    val inactive = !song.isActive

    ListItem(
        modifier = modifier
            .then(if (inactive) Modifier.alpha(0.4f) else Modifier)
            .then(
                if (!inactive) {
                    if (onLongClick != null) Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClickLabel = stringResource(R.string.cd_open_song_menu),
                        onLongClick = onLongClick
                    ) else Modifier.clickable(onClick = onClick)
                } else Modifier
            ),
        colors = if (selected) ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else ListItemDefaults.colors(),
        leadingContent = {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = stringResource(R.string.cd_album_art),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                AlbumArtPlaceholder(
                    modifier = Modifier.size(48.dp),
                    iconSize = 24.dp
                )
            }
        },
        headlineContent = {
            Text(
                text = song.title,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val subtitle = when {
                showAlbumInSubtitle && song.artistName.isNotBlank() && song.albumName.isNotBlank() -> "${song.artistName} \u2022 ${song.albumName}"
                song.artistName.isNotBlank() -> song.artistName
                showAlbumInSubtitle && song.albumName.isNotBlank() -> song.albumName
                else -> ""
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            if (showDragHandle || onRemoveClick != null) {
                Row {
                    if (showDragHandle) {
                        Box(modifier = dragHandleModifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_move_song)
                            )
                        }
                    }
                    if (onRemoveClick != null) {
                        IconButton(onClick = onRemoveClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.cd_remove_from_playlist),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else if (onFavoriteClick != null) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (song.isFavorite) stringResource(R.string.cd_remove_from_favorites) else stringResource(R.string.cd_add_to_favorites),
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
