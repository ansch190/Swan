package com.schwanitz.data.repository

import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.entity.SongLyricsEntity
import com.schwanitz.domain.repository.SongLyricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongLyricsRepositoryImpl @Inject constructor(
    private val songLyricsDao: SongLyricsDao
) : SongLyricsRepository {

    override suspend fun getLyrics(songId: String): String? {
        return songLyricsDao.getLyrics(songId)?.lyrics
    }

    override suspend fun saveLyrics(songId: String, lyrics: String) {
        songLyricsDao.insertLyrics(SongLyricsEntity(songId, lyrics, System.currentTimeMillis()))
    }

    override suspend fun deleteBySource(sourceId: String) {
        songLyricsDao.deleteBySource(sourceId)
    }
}
