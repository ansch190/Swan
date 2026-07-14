package com.schwanitz.ui.common

import com.schwanitz.domain.model.Song

fun List<Song>.filterSongs(query: String, favoritesOnly: Boolean): List<Song> = when {
    query.isNotBlank() -> filter {
        it.title.contains(query, ignoreCase = true) ||
                it.artistName.contains(query, ignoreCase = true) ||
                it.albumName.contains(query, ignoreCase = true)
    }
    favoritesOnly -> filter { it.isFavorite }
    else -> this
}
