package com.schwanitz.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.schwanitz.data.local.dao.AlbumArtworkDao
import com.schwanitz.data.local.dao.AlbumDao
import com.schwanitz.data.local.dao.AlbumSeriesDao
import com.schwanitz.data.local.dao.AlbumSongDao
import com.schwanitz.data.local.dao.ArtistDao
import com.schwanitz.data.local.dao.ArtistPicDao
import com.schwanitz.data.local.dao.PlaylistDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.dao.SongTechnicalInfoDao
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.data.local.entity.AlbumArtworkEntity
import com.schwanitz.data.local.entity.AlbumEntity
import com.schwanitz.data.local.entity.AlbumSeriesEntity
import com.schwanitz.data.local.entity.AlbumSeriesMappingEntity
import com.schwanitz.data.local.entity.AlbumSongMappingEntity
import com.schwanitz.data.local.entity.ArtistEntity
import com.schwanitz.data.local.entity.ArtistPicEntity
import com.schwanitz.data.local.entity.PlaylistEntity
import com.schwanitz.data.local.entity.PlaylistSongMapping
import com.schwanitz.data.local.entity.SongEntity
import com.schwanitz.data.local.entity.SongLyricsEntity
import com.schwanitz.data.local.entity.SongTechnicalInfoEntity
import com.schwanitz.data.local.entity.SourceConfigEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongMapping::class,
        SourceConfigEntity::class,
        ArtistEntity::class,
        ArtistPicEntity::class,
        SongLyricsEntity::class,
        AlbumSeriesEntity::class,
        AlbumSeriesMappingEntity::class,
        AlbumEntity::class,
        AlbumArtworkEntity::class,
        SongTechnicalInfoEntity::class,
        AlbumSongMappingEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun sourceConfigDao(): SourceConfigDao
    abstract fun albumDao(): AlbumDao
    abstract fun albumArtworkDao(): AlbumArtworkDao
    abstract fun artistDao(): ArtistDao
    abstract fun artistPicDao(): ArtistPicDao
    abstract fun songLyricsDao(): SongLyricsDao
    abstract fun albumSeriesDao(): AlbumSeriesDao
    abstract fun albumSongDao(): AlbumSongDao
    abstract fun songTechnicalInfoDao(): SongTechnicalInfoDao
}
