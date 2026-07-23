package com.schwanitz.data.genius

import com.schwanitz.domain.repository.SongLyricsRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeniusLyricsProvider @Inject constructor(
    private val apiService: GeniusApiService,
    private val lyricsRepository: SongLyricsRepository
) {

    suspend fun getLyrics(songId: String, title: String, artist: String): String? {
        lyricsRepository.getLyrics(songId)?.let { cached ->
            Timber.d("Lyrics cache HIT for '%s' by %s", title, artist)
            return cached
        }

        Timber.d("Fetching lyrics from Genius for '%s' by %s", title, artist)
        val lyrics = apiService.searchLyrics(title, artist) ?: return null

        lyricsRepository.saveLyrics(songId, lyrics)
        Timber.d("Lyrics cached for songId=%s", songId)
        return lyrics
    }
}
