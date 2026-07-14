package com.schwanitz.ui.navigation

import android.net.Uri

object Routes {
    const val SETTINGS = "settings"
    const val GENERAL_SETTINGS = "general_settings"
    const val SOURCE_SETTINGS = "source_settings"
    const val ABOUT = "about"
    const val ADD_SOURCE = "add_source"
    const val ALL_ARTISTS = "all_artists"
    const val ALL_ALBUMS = "all_albums"
    const val ALL_YEARS = "all_years"
    const val ALL_GENRES = "all_genres"
    const val ALL_SERIES = "all_series"

    fun songInfo(songId: String) = "song_info/${Uri.encode(songId)}"

    fun albumDetail(albumName: String, albumArtistName: String) =
        "album_detail/${Uri.encode(albumName)}/${Uri.encode(albumArtistName)}"

    fun artistDetail(artistName: String) = "artist_detail/${Uri.encode(artistName)}"

    fun genreDetail(genreName: String) = "genre_detail/${Uri.encode(genreName)}"

    fun yearDetail(year: Int) = "year_detail/$year"

    fun seriesDetail(seriesName: String) = "series_detail/${Uri.encode(seriesName)}"

    fun playlistDetail(playlistId: Long) = "playlist_detail/$playlistId"

    fun selectSongs(playlistId: Long) = "select_songs/$playlistId"

    fun addSource(sourceId: String?) =
        if (sourceId != null) "add_source?sourceId=$sourceId" else ADD_SOURCE
}
