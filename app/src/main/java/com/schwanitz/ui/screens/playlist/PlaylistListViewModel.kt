package com.schwanitz.ui.screens.playlist

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schwanitz.R
import com.schwanitz.domain.repository.MusicRepository
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
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val favoritesCount = musicRepository.getFavoriteSongs()
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
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
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
            musicRepository.getFavoriteSongs().first()
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
        val converted = songs.map { it.copy(id = uriToPlaylistPath(it.id)) }
        return when (format) {
            PlaylistExportFormat.M3U -> buildM3u(name, converted)
            PlaylistExportFormat.PLS -> buildPls(name, converted)
            PlaylistExportFormat.XSPF -> buildXspf(name, converted)
            PlaylistExportFormat.WPL -> buildWpl(name, converted)
            PlaylistExportFormat.ASX -> buildAsx(name, converted)
            PlaylistExportFormat.B4S -> buildB4s(name, converted)
        }
    }

    private fun uriToPlaylistPath(uriStr: String): String {
        if (!uriStr.startsWith("content://")) return uriStr
        val uri = Uri.parse(uriStr)
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":", limit = 2)
            val volume = parts[0]
            val path = if (parts.size > 1) parts[1] else ""
            when (volume) {
                "primary" -> "/storage/emulated/0/$path"
                else -> "/storage/$volume/$path"
            }
        } catch (_: Exception) {
            uriStr
        }
    }

    private fun buildM3u(name: String, songs: List<Song>): String = buildString {
        appendLine("#EXTM3U")
        for (song in songs) {
            val seconds = song.durationMs / 1000
            val display = if (song.artist.isNotBlank()) "${song.artist} - ${song.title}" else song.title
            appendLine("#EXTINF:$seconds,$display")
            appendLine(song.id)
        }
    }

    private fun buildPls(name: String, songs: List<Song>): String = buildString {
        appendLine("[playlist]")
        appendLine("NumberOfEntries=${songs.size}")
        songs.forEachIndexed { i, song ->
            val n = i + 1
            appendLine("File$n=${song.id}")
            appendLine("Title$n=${song.title}")
            appendLine("Length$n=${song.durationMs / 1000}")
        }
        appendLine("Version=2")
    }

    private fun buildXspf(name: String, songs: List<Song>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">")
        appendLine("    <title>${escapeXml(name)}</title>")
        appendLine("    <trackList>")
        for (song in songs) {
            appendLine("        <track>")
            if (song.title.isNotBlank()) appendLine("            <title>${escapeXml(song.title)}</title>")
            if (song.artist.isNotBlank()) appendLine("            <creator>${escapeXml(song.artist)}</creator>")
            if (song.album.isNotBlank()) appendLine("            <album>${escapeXml(song.album)}</album>")
            appendLine("            <duration>${song.durationMs}</duration>")
            appendLine("            <location>${escapeXml(song.id)}</location>")
            appendLine("        </track>")
        }
        appendLine("    </trackList>")
        appendLine("</playlist>")
    }

    private fun buildWpl(name: String, songs: List<Song>): String = buildString {
        appendLine("<?wpl version=\"1.0\"?>")
        appendLine("<smil>")
        appendLine("    <head>")
        appendLine("        <title>${escapeXml(name)}</title>")
        appendLine("    </head>")
        appendLine("    <body>")
        appendLine("        <seq>")
        for (song in songs) {
            appendLine("            <media src=\"${escapeXml(song.id)}\"/>")
        }
        appendLine("        </seq>")
        appendLine("    </body>")
        appendLine("</smil>")
    }

    private fun buildAsx(name: String, songs: List<Song>): String = buildString {
        appendLine("<asx version=\"3.0\">")
        appendLine("    <title>${escapeXml(name)}</title>")
        for (song in songs) {
            appendLine("    <entry>")
            if (song.title.isNotBlank()) appendLine("        <title>${escapeXml(song.title)}</title>")
            if (song.artist.isNotBlank()) appendLine("        <author>${escapeXml(song.artist)}</author>")
            appendLine("        <ref href=\"${escapeXml(song.id)}\"/>")
            appendLine("    </entry>")
        }
        appendLine("</asx>")
    }

    private fun buildB4s(name: String, songs: List<Song>): String = buildString {
        appendLine("<playlist>")
        for (song in songs) {
            val path = escapeXml(song.id)
            if (song.title.isNotBlank()) {
                appendLine("<entry playstring=\"file:${path}\">")
                appendLine("    <name>${escapeXml(song.title)}</name>")
                appendLine("    <length>${song.durationMs / 1000}</length>")
                appendLine("</entry>")
            } else {
                appendLine("<entry playstring=\"file:${path}\"/>")
            }
        }
        appendLine("</playlist>")
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
