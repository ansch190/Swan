package com.schwanitz.di

import com.schwanitz.data.repository.AlbumRepositoryImpl
import com.schwanitz.data.repository.ArtistRepositoryImpl
import com.schwanitz.data.repository.PlaylistRepositoryImpl
import com.schwanitz.data.repository.SeriesRepositoryImpl
import com.schwanitz.data.repository.SongLyricsRepositoryImpl
import com.schwanitz.data.repository.SongRepositoryImpl
import com.schwanitz.data.repository.SourceLifecycleManagerImpl
import com.schwanitz.data.repository.SourceManagerImpl
import com.schwanitz.domain.repository.AlbumRepository
import com.schwanitz.domain.repository.ArtistRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.domain.repository.SeriesRepository
import com.schwanitz.domain.repository.SongLyricsRepository
import com.schwanitz.domain.repository.SongRepository
import com.schwanitz.domain.repository.SourceLifecycleManager
import com.schwanitz.domain.repository.SourceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.schwanitz.data.source.LocalFolderMusicSource
import com.schwanitz.data.source.SmbMusicSource
import com.schwanitz.data.source.WebDavMusicSource
import com.schwanitz.domain.source.MusicSource
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindSeriesRepository(impl: SeriesRepositoryImpl): SeriesRepository

    @Binds
    @Singleton
    abstract fun bindSourceLifecycleManager(impl: SourceLifecycleManagerImpl): SourceLifecycleManager

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSourceManager(impl: SourceManagerImpl): SourceManager

    @Binds
    @Singleton
    abstract fun bindArtistRepository(impl: ArtistRepositoryImpl): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindSongLyricsRepository(impl: SongLyricsRepositoryImpl): SongLyricsRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {

    @Provides
    @IntoSet
    fun provideLocalMusicSource(source: LocalFolderMusicSource): MusicSource = source

    @Provides
    @IntoSet
    fun provideWebDavMusicSource(source: WebDavMusicSource): MusicSource = source

    @Provides
    @IntoSet
    fun provideSmbMusicSource(source: SmbMusicSource): MusicSource = source
}
