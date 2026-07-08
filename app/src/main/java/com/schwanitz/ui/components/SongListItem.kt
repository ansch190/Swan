package com.schwanitz.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import com.schwanitz.ui.components.MarqueeText

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
    selected: Boolean = false
) {
    val inactive = !song.isActive

    ListItem(
        modifier = modifier
            .then(if (inactive) Modifier.alpha(0.4f) else Modifier)
            .then(
                if (!inactive) {
                    if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    else Modifier.combinedClickable(onClick = onClick, onLongClick = {})
                } else Modifier
            ),
        colors = if (selected) ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else ListItemDefaults.colors(),
        leadingContent = {
            if (song.albumArtUri != null) {
                AsyncImage(
                    model = song.albumArtUri,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = stringResource(R.string.cd_album_art),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        headlineContent = {
            MarqueeText(
                text = song.title,
                modifier = Modifier.fillMaxWidth()
            )
        },
        supportingContent = {
            val subtitle = when {
                song.artist.isNotBlank() && song.album.isNotBlank() -> "${song.artist} \u2022 ${song.album}"
                song.artist.isNotBlank() -> song.artist
                song.album.isNotBlank() -> song.album
                else -> ""
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            if (showDragHandle || onRemoveClick != null) {
                Row {
                    if (showDragHandle) {
                        IconButton(modifier = dragHandleModifier, onClick = {}) {
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
