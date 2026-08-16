package com.schwanitz.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.schwanitz.data.local.entity.ScanArtworkEntity
import com.schwanitz.data.local.entity.ScanDiscoveredEntity
import com.schwanitz.data.local.entity.ScanSessionEntity
import com.schwanitz.data.local.entity.ScanSongEntity

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiscovered(entries: List<ScanDiscoveredEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(entries: List<ScanSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtworks(entries: List<ScanArtworkEntity>)

    @Query("SELECT * FROM scan_songs WHERE sessionId = :sessionId")
    suspend fun getSongs(sessionId: String): List<ScanSongEntity>

    @Query("SELECT * FROM scan_artworks WHERE sessionId = :sessionId")
    suspend fun getArtworks(sessionId: String): List<ScanArtworkEntity>

    @Query("SELECT songId FROM scan_discovered WHERE sessionId = :sessionId")
    suspend fun getDiscoveredIds(sessionId: String): List<String>

    @Query("DELETE FROM scan_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM scan_sessions WHERE sourceId = :sourceId")
    suspend fun deleteSessionsForSource(sourceId: String)

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAllSessions()
}
