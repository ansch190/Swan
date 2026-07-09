package com.schwanitz.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.schwanitz.data.local.dao.AlbumSeriesDao
import com.schwanitz.data.local.dao.ArtistImageDao
import com.schwanitz.data.local.dao.ArtistProfileDao
import com.schwanitz.data.local.dao.PlaylistDao
import com.schwanitz.data.local.dao.SongArtworkDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.data.local.entity.AlbumSeriesEntity
import com.schwanitz.data.local.entity.AlbumSeriesMappingEntity
import com.schwanitz.data.local.entity.ArtistImageEntity
import com.schwanitz.data.local.entity.ArtistProfileEntity
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongCrossRef
import com.schwanitz.data.local.entity.SongArtworkEntity
import com.schwanitz.data.local.entity.SongEntity
import com.schwanitz.data.local.entity.SongLyricsEntity
import com.schwanitz.data.local.entity.SourceConfigEntity

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class, SourceConfigEntity::class, SongArtworkEntity::class, ArtistImageEntity::class, ArtistProfileEntity::class, SongLyricsEntity::class, AlbumSeriesEntity::class, AlbumSeriesMappingEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun sourceConfigDao(): SourceConfigDao
    abstract fun songArtworkDao(): SongArtworkDao
    abstract fun artistImageDao(): ArtistImageDao
    abstract fun artistProfileDao(): ArtistProfileDao
    abstract fun songLyricsDao(): SongLyricsDao
    abstract fun albumSeriesDao(): AlbumSeriesDao
}
