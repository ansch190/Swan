package com.schwanitz.domain.repository

interface SongLyricsRepository {
    suspend fun getLyrics(songId: String): String?
    suspend fun saveLyrics(songId: String, lyrics: String)
    suspend fun deleteBySource(sourceId: String)
}
