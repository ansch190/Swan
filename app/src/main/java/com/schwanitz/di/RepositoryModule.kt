package com.schwanitz.di

import com.schwanitz.data.repository.ArtistRepositoryImpl
import com.schwanitz.data.repository.MusicRepositoryImpl
import com.schwanitz.data.repository.PlaylistRepositoryImpl
import com.schwanitz.data.repository.SourceManagerImpl
import com.schwanitz.domain.repository.ArtistRepository
import com.schwanitz.domain.repository.MusicRepository
import com.schwanitz.domain.repository.PlaylistRepository
import com.schwanitz.domain.repository.SourceManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.schwanitz.data.source.LocalFolderMusicSource
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
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSourceManager(impl: SourceManagerImpl): SourceManager

    @Binds
    @Singleton
    abstract fun bindArtistRepository(impl: ArtistRepositoryImpl): ArtistRepository
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
}
