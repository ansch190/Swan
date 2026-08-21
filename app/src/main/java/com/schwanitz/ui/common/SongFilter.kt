package com.schwanitz.ui.common

import com.schwanitz.domain.model.Song
import java.text.Normalizer
import java.util.Locale

private fun String.normalizedForSearch(): String = Normalizer
    .normalize(this, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
    .lowercase(Locale.ROOT)

fun List<Song>.filterSongs(query: String, favoritesOnly: Boolean): List<Song> {
    val normalizedQuery = query.trim().normalizedForSearch()
    if (!favoritesOnly && normalizedQuery.isEmpty()) return this
    return filter { song ->
        (!favoritesOnly || song.isFavorite) && (
            normalizedQuery.isEmpty() ||
                song.title.normalizedForSearch().contains(normalizedQuery) ||
                song.artistName.normalizedForSearch().contains(normalizedQuery) ||
                song.albumName.normalizedForSearch().contains(normalizedQuery)
            )
    }
}
