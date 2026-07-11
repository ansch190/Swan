package com.schwanitz.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.schwanitz.R
import com.schwanitz.domain.model.Song

@Composable
fun SelectableSongItem(
    song: Song,
    isSelecting: Boolean,
    isSelected: Boolean,
    onSongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    onPlayAll: () -> Unit,
    onPlaySelection: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    extraMenuItems: @Composable () -> Unit = {}
) {
    var showNormalMenu by remember { mutableStateOf(false) }
    var showSelectionMenu by remember { mutableStateOf(false) }

    Box {
        SongListItem(
            song = song,
            onClick = {
                if (isSelecting) onToggleSelection()
                else onSongClick()
            },
            onLongClick = {
                if (isSelecting && isSelected) showSelectionMenu = true
                else showNormalMenu = true
            },
            selected = isSelecting && isSelected
        )

        DropdownMenu(
            expanded = showNormalMenu,
            onDismissRequest = { showNormalMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_selection)) },
                onClick = {
                    showNormalMenu = false
                    onEnterSelection()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_play_all)) },
                onClick = {
                    showNormalMenu = false
                    onPlayAll()
                }
            )
            extraMenuItems()
        }

        DropdownMenu(
            expanded = showSelectionMenu,
            onDismissRequest = { showSelectionMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_selection_play)) },
                onClick = {
                    showSelectionMenu = false
                    onPlaySelection()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_add_to_playlist)) },
                onClick = {
                    showSelectionMenu = false
                    onAddToPlaylist()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.context_add_to_queue)) },
                onClick = {
                    showSelectionMenu = false
                    onAddToQueue()
                }
            )
        }
    }
}
