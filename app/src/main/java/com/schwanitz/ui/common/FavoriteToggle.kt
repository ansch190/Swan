package com.schwanitz.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.MusicRepository
import kotlinx.coroutines.launch

fun ViewModel.toggleFavorite(
    song: Song,
    musicRepository: MusicRepository,
    errorHolder: ErrorHolder
) {
    viewModelScope.launch {
        runCatching { musicRepository.toggleFavorite(song.id) }
            .exceptionOrNull()?.let { errorHolder.emit(it) }
    }
}
