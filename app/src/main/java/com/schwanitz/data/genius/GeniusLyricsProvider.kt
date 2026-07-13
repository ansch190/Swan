package com.schwanitz.data.genius

import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.entity.SongLyricsEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeniusLyricsProvider @Inject constructor(
    private val apiService: GeniusApiService,
    private val lyricsDao: SongLyricsDao
) {

    suspend fun getLyrics(songId: String, title: String, artist: String): String? {
        lyricsDao.getLyrics(songId)?.let { cached ->
            Timber.d("Lyrics cache HIT for '%s' by %s", title, artist)
            return cached.lyrics
        }

        Timber.d("Fetching lyrics from Genius for '%s' by %s", title, artist)
        val lyrics = apiService.searchLyrics(title, artist) ?: return null

        lyricsDao.insertLyrics(SongLyricsEntity(songId, lyrics, System.currentTimeMillis()))
        Timber.d("Lyrics cached for songId=%s", songId)
        return lyrics
    }
}
