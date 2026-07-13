package com.schwanitz.ui.screens.playlist

import android.net.Uri
import android.provider.DocumentsContract
import com.schwanitz.domain.model.Song

object PlaylistExporter {

    fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun parseDocumentId(docId: String): String {
        val parts = docId.split(":", limit = 2)
        val volume = parts[0]
        val path = if (parts.size > 1) parts[1] else ""
        return when (volume) {
            "primary" -> "/storage/emulated/0/$path"
            else -> "/storage/$volume/$path"
        }
    }

    fun uriToPlaylistPath(uriStr: String): String {
        if (!uriStr.startsWith("content://")) return uriStr
        val uri = Uri.parse(uriStr)
        return try {
            parseDocumentId(DocumentsContract.getDocumentId(uri))
        } catch (_: Exception) {
            uriStr
        }
    }

    fun buildM3u(songs: List<Song>): String = buildString {
        appendLine("#EXTM3U")
        for (song in songs) {
            val seconds = song.durationMs / 1000
            val display = if (song.artistName.isNotBlank()) "${song.artistName} - ${song.title}" else song.title
            appendLine("#EXTINF:$seconds,$display")
            appendLine(song.id)
        }
    }

    fun buildPls(songs: List<Song>): String = buildString {
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

    fun buildXspf(name: String, songs: List<Song>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">")
        appendLine("    <title>${escapeXml(name)}</title>")
        appendLine("    <trackList>")
        for (song in songs) {
            appendLine("        <track>")
            if (song.title.isNotBlank()) appendLine("            <title>${escapeXml(song.title)}</title>")
            if (song.artistName.isNotBlank()) appendLine("            <creator>${escapeXml(song.artistName)}</creator>")
            if (song.albumName.isNotBlank()) appendLine("            <album>${escapeXml(song.albumName)}</album>")
            appendLine("            <duration>${song.durationMs}</duration>")
            appendLine("            <location>${escapeXml(song.id)}</location>")
            appendLine("        </track>")
        }
        appendLine("    </trackList>")
        appendLine("</playlist>")
    }

    fun buildWpl(name: String, songs: List<Song>): String = buildString {
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

    fun buildAsx(name: String, songs: List<Song>): String = buildString {
        appendLine("<asx version=\"3.0\">")
        appendLine("    <title>${escapeXml(name)}</title>")
        for (song in songs) {
            appendLine("    <entry>")
            if (song.title.isNotBlank()) appendLine("        <title>${escapeXml(song.title)}</title>")
            if (song.artistName.isNotBlank()) appendLine("        <author>${escapeXml(song.artistName)}</author>")
            appendLine("        <ref href=\"${escapeXml(song.id)}\"/>")
            appendLine("    </entry>")
        }
        appendLine("</asx>")
    }

    fun buildB4s(songs: List<Song>): String = buildString {
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

    fun prepareSongPaths(songs: List<Song>): List<Song> =
        songs.map { it.copy(id = uriToPlaylistPath(it.id)) }
}
