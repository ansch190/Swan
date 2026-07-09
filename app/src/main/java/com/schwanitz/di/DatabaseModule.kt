package com.schwanitz.di

import android.content.Context
import androidx.room.Room
import com.schwanitz.data.local.AppDatabase
import com.schwanitz.data.local.dao.AlbumSeriesDao
import com.schwanitz.data.local.dao.ArtistImageDao
import com.schwanitz.data.local.dao.ArtistProfileDao
import com.schwanitz.data.local.dao.PlaylistDao
import com.schwanitz.data.local.dao.SongArtworkDao
import com.schwanitz.data.local.dao.SongDao
import com.schwanitz.data.local.dao.SongLyricsDao
import com.schwanitz.data.local.dao.SourceConfigDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "music_player_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideSongDao(db: AppDatabase): SongDao = db.songDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideSourceConfigDao(db: AppDatabase): SourceConfigDao = db.sourceConfigDao()

    @Provides
    fun provideSongArtworkDao(db: AppDatabase): SongArtworkDao = db.songArtworkDao()

    @Provides
    fun provideArtistImageDao(db: AppDatabase): ArtistImageDao = db.artistImageDao()

    @Provides
    fun provideArtistProfileDao(db: AppDatabase): ArtistProfileDao = db.artistProfileDao()

    @Provides
    fun provideSongLyricsDao(db: AppDatabase): SongLyricsDao = db.songLyricsDao()

    @Provides
    fun provideAlbumSeriesDao(db: AppDatabase): AlbumSeriesDao = db.albumSeriesDao()
}
