package com.schwanitz.ui.screens.songinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.data.genius.GeniusLyricsProvider
import com.schwanitz.domain.model.AlbumArtwork
import com.schwanitz.domain.model.AlbumSeries
import com.schwanitz.domain.model.Song
import com.schwanitz.domain.repository.AlbumRepository
import com.schwanitz.domain.repository.SeriesRepository
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.SourceManager
import com.schwanitz.ui.common.ErrorHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SongInfoViewModel @Inject constructor(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val seriesRepository: SeriesRepository,
    private val sourceManager: SourceManager,
    private val lyricsProvider: GeniusLyricsProvider
) : ViewModel() {

    private val _song = MutableStateFlow<Song?>(null)
    val song: StateFlow<Song?> = _song

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName

    private val _artworks = MutableStateFlow<List<AlbumArtwork>>(emptyList())
    val artworks: StateFlow<List<AlbumArtwork>> = _artworks

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics

    private val _trackTotal = MutableStateFlow(0)
    val trackTotal: StateFlow<Int> = _trackTotal

    private val _discTotal = MutableStateFlow(0)
    val discTotal: StateFlow<Int> = _discTotal

    private val _series = MutableStateFlow<AlbumSeries?>(null)
    val series: StateFlow<AlbumSeries?> = _series

    val errorHolder = ErrorHolder()

    fun loadSong(songId: String) {
        viewModelScope.launch {
            runCatching {
                val s = songRepository.getSongById(songId)
                _song.value = s
                if (s != null) {
                    Timber.d("Loaded song: '%s' by %s", s.title, s.artistName)
                    if (s.albumId != null) {
                        launch {
                            runCatching {
                                seriesRepository.getSeriesForAlbum(s.albumId).collect {
                                    _series.value = it
                                }
                            }.exceptionOrNull()?.let { errorHolder.emit(it) }
                        }
                        launch {
                            runCatching {
                                _trackTotal.value = albumRepository.getTrackTotal(s.albumId, s.discNumber)
                                _discTotal.value = albumRepository.getDiscTotal(s.albumId)
                            }.exceptionOrNull()?.let { errorHolder.emit(it) }
                        }
                    }
                    val config = sourceManager.getSourceById(s.sourceId)
                    _sourceName.value = config?.name ?: s.sourceId
                    _artworks.value = if (s.albumId != null) {
                        albumRepository.getAlbumArtworks(s.albumId)
                    } else {
                        emptyList()
                    }
                    _lyrics.value = lyricsProvider.getLyrics(songId, s.title, s.artistName)
                }
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }
}
