package com.schwanitz.ui.screens.albumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.domain.model.Album
import com.schwanitz.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AlbumListViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _allAlbums = MutableStateFlow<List<Album>>(emptyList())
    val allAlbums: StateFlow<List<Album>> = _allAlbums

    fun loadAlbums() {
        viewModelScope.launch {
            songRepository.getAllAlbums().collect {
                val withArt = it.count { a -> a.albumArtUri != null }
                Timber.d("AlbumList: %d albums loaded, %d with artwork", it.size, withArt)
                _allAlbums.value = it
            }
        }
    }
}
