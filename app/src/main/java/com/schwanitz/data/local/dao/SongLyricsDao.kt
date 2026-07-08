package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.schwanitz.data.local.entity.SongLyricsEntity

@Dao
interface SongLyricsDao {

    @Query("SELECT * FROM song_lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: String): SongLyricsEntity?

    @Upsert
    suspend fun insertLyrics(lyrics: SongLyricsEntity)

    @Query("DELETE FROM song_lyrics WHERE songId IN (SELECT id FROM songs WHERE sourceId = :sourceId)")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM song_lyrics WHERE songId NOT IN (SELECT id FROM songs)")
    suspend fun deleteOrphaned()

    @Query("DELETE FROM song_lyrics")
    suspend fun clearAll()
}
