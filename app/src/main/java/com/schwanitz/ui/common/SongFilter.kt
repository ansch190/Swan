package com.schwanitz.ui.common

import com.schwanitz.domain.model.Song

fun List<Song>.filterSongs(query: String, favoritesOnly: Boolean): List<Song> = this
    .let { list -> if (favoritesOnly) list.filter { it.isFavorite } else list }
    .let { list ->
        if (query.isNotBlank()) list.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true) ||
                    it.albumName.contains(query, ignoreCase = true)
        } else list
    }
