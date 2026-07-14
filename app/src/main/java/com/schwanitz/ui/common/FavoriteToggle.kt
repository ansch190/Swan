package com.schwanitz.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.SongRepository
import kotlinx.coroutines.launch

fun ViewModel.toggleFavorite(
    song: Song,
    songRepository: SongRepository,
    errorHolder: ErrorHolder
) {
    viewModelScope.launch {
        runCatching { songRepository.toggleFavorite(song.id) }
            .exceptionOrNull()?.let { errorHolder.emit(it) }
    }
}
