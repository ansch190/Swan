package com.schwanitz.ui.navigation

import android.net.Uri

object Routes {
    const val COLLECTION = "collection"
    const val SETTINGS = "settings"
    const val GENERAL_SETTINGS = "general_settings"
    const val SOURCE_SETTINGS = "source_settings"
    const val ABOUT = "about"
    const val BACKUP = "backup"
    const val ARTIST_DATA_SOURCE = "artist_data_source"
    const val ADD_SOURCE = "add_source"
    fun playlistDetail(playlistId: Long) = "playlist_detail/$playlistId"

    fun selectSongs(playlistId: Long) = "select_songs/$playlistId"

    fun addSource(sourceId: String?) =
        if (sourceId != null) "add_source?sourceId=$sourceId" else ADD_SOURCE

    internal fun scoped(owner: BottomNavItem): ScopedRoutes = ScopedRoutes(owner.route)
}

internal class ScopedRoutes(private val prefix: String) {
    val songInfoPattern = "$prefix/song_info/{songId}"
    val albumDetailPattern = "$prefix/album_detail/{albumName}/{albumArtistName}/{albumYear}"
    val artistDetailPattern = "$prefix/artist_detail/{artistName}"
    val genreDetailPattern = "$prefix/genre_detail/{genreName}"
    val yearDetailPattern = "$prefix/year_detail/{year}"
    val decadeDetailPattern = "$prefix/decade_detail/{decade}"
    val seriesDetailPattern = "$prefix/series_detail/{seriesName}"
    val artistBiographyPattern = "$prefix/artist_biography/{artistName}"
    val playlistPickerPattern = "$prefix/playlist_picker/{songIds}"

    val allArtists = "$prefix/all_artists"
    val allAlbums = "$prefix/all_albums"
    val allYears = "$prefix/all_years"
    val allGenres = "$prefix/all_genres"
    val allSeries = "$prefix/all_series"

    fun songInfo(songId: String) = "$prefix/song_info/${Uri.encode(songId)}"

    fun albumDetail(albumName: String, albumArtistName: String, year: Int) =
        "$prefix/album_detail/${Uri.encode(albumName)}/${Uri.encode(albumArtistName)}/$year"

    fun artistDetail(artistName: String) = "$prefix/artist_detail/${Uri.encode(artistName)}"

    fun genreDetail(genreName: String) = "$prefix/genre_detail/${Uri.encode(genreName)}"

    fun yearDetail(year: Int) = "$prefix/year_detail/$year"

    fun decadeDetail(decade: Int) = "$prefix/decade_detail/$decade"

    fun seriesDetail(seriesName: String) = "$prefix/series_detail/${Uri.encode(seriesName)}"

    fun artistBiography(artistName: String) =
        "$prefix/artist_biography/${Uri.encode(artistName)}"

    fun playlistPicker(songIds: String) = "$prefix/playlist_picker/${Uri.encode(songIds)}"
}
