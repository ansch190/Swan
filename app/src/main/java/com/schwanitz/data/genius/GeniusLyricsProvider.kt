package com.schwanitz.data.genius

import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.entity.SongLyricsEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeniusLyricsProvider @Inject constructor(
    private val apiService: GeniusApiService,
    private val lyricsDao: SongLyricsDao
) {

    suspend fun getLyrics(songId: String, title: String, artist: String): String? {
        lyricsDao.getLyrics(songId)?.let { cached ->
            return cached.lyrics
        }

        val lyrics = apiService.searchLyrics(title, artist) ?: return null

        lyricsDao.insertLyrics(SongLyricsEntity(songId, lyrics, System.currentTimeMillis()))
        return lyrics
    }
}
