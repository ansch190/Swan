package com.schwanitz.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.schwanitz.data.local.dao.PlaylistDao
import com.schwanitz.data.local.dao.SongArtworkDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongCrossRef
import com.schwanitz.data.local.entity.SongArtworkEntity
import com.schwanitz.data.local.entity.SongEntity
import com.schwanitz.data.local.entity.SourceConfigEntity

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class, SourceConfigEntity::class, SongArtworkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun sourceConfigDao(): SourceConfigDao
    abstract fun songArtworkDao(): SongArtworkDao
}
