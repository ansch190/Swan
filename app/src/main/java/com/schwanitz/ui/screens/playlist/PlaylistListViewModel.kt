package com.schwanitz.ui.screens.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.R
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.schwanitz.domain.model.Song
import com.schwanitz.ui.common.ErrorHolder
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class PlaylistListItemData(
    val id: Long,
    val name: String,
    val songCount: Int,
    val isFavorite: Boolean = false
)

data class PlaylistListUiState(
    val playlists: List<PlaylistListItemData> = emptyList()
)

@HiltViewModel
class PlaylistListViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val errorHolder = ErrorHolder()

    private val favoritesCount = songRepository.getFavoriteSongs()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<PlaylistListUiState> =
        combine(
            playlistRepository.getAllPlaylists(),
            playlistRepository.getAllPlaylistSongCounts(),
            favoritesCount
        ) { playlists, counts, favCount ->
            val items = playlists.map { p ->
                PlaylistListItemData(
                    id = p.id,
                    name = p.name,
                    songCount = counts[p.id] ?: 0
                )
            }
            val favoritesItem = PlaylistListItemData(
                id = -1L,
                name = context.getString(R.string.playlist_favorites_name),
                songCount = favCount,
                isFavorite = true
            )
            PlaylistListUiState(
                playlists = listOf(favoritesItem) + items
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistListUiState())

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            runCatching {
                playlistRepository.createPlaylist(name)
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runCatching {
                playlistRepository.deletePlaylist(playlistId)
            }.exceptionOrNull()?.let { errorHolder.emit(it) }
        }
    }

    enum class PlaylistExportFormat(
        val labelRes: Int,
        val descriptionRes: Int,
        val extension: String,
        val mimeType: String
    ) {
        M3U(R.string.export_format_m3u, R.string.export_format_m3u_desc, "m3u", "audio/x-mpegurl"),
        PLS(R.string.export_format_pls, R.string.export_format_pls_desc, "pls", "audio/x-scpls"),
        XSPF(R.string.export_format_xspf, R.string.export_format_xspf_desc, "xspf", "application/xspf+xml"),
        WPL(R.string.export_format_wpl, R.string.export_format_wpl_desc, "wpl", "application/vnd.ms-wpl"),
        ASX(R.string.export_format_asx, R.string.export_format_asx_desc, "asx", "video/x-ms-asf"),
        B4S(R.string.export_format_b4s, R.string.export_format_b4s_desc, "b4s", "application/x-winamp-playlist")
    }

    suspend fun getPlaylistExportContent(
        playlistId: Long,
        playlistName: String,
        format: PlaylistExportFormat
    ): String {
        val songs = if (playlistId == -1L) {
            songRepository.getFavoriteSongs().first()
        } else {
            playlistRepository.getPlaylistSongs(playlistId).first()
        }
        return buildExportContent(playlistName, songs, format)
    }

    private fun buildExportContent(
        name: String,
        songs: List<Song>,
        format: PlaylistExportFormat
    ): String {
        val converted = PlaylistExporter.prepareSongPaths(songs)
        return when (format) {
            PlaylistExportFormat.M3U -> PlaylistExporter.buildM3u(converted)
            PlaylistExportFormat.PLS -> PlaylistExporter.buildPls(converted)
            PlaylistExportFormat.XSPF -> PlaylistExporter.buildXspf(name, converted)
            PlaylistExportFormat.WPL -> PlaylistExporter.buildWpl(name, converted)
            PlaylistExportFormat.ASX -> PlaylistExporter.buildAsx(name, converted)
            PlaylistExportFormat.B4S -> PlaylistExporter.buildB4s(converted)
        }
    }
}
