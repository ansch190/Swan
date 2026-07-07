package com.schwanitz.ui.screens.songinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.model.SongArtwork
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.SourceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongInfoViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sourceManager: SourceManager
) : ViewModel() {

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName

    private val _artworks = MutableStateFlow<List<SongArtwork>>(emptyList())
    val artworks: StateFlow<List<SongArtwork>> = _artworks

    fun loadSong(songId: String) {
        viewModelScope.launch {
            val s = musicRepository.getSongById(songId)
            _song.value = s
            if (s != null) {
                val config = sourceManager.getSourceById(s.sourceId)
                _sourceName.value = config?.name ?: s.sourceId
                _artworks.value = musicRepository.getSongArtworks(songId)
            }
        }
    }
}
