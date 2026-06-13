package com.schwanitz.swan.di

import android.content.Context
import com.schwanitz.swan.data.local.database.AppDatabase
import com.schwanitz.swan.data.local.repository.ArtistImageRepository
import com.schwanitz.swan.data.local.repository.MusicRepository
import com.schwanitz.swan.domain.repository.ArtistImageRepository as ArtistImageRepositoryInterface
import com.schwanitz.swan.domain.repository.MusicRepository as MusicRepositoryInterface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideMusicRepository(@ApplicationContext context: Context): MusicRepositoryInterface {
        return MusicRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideArtistImageRepository(db: AppDatabase): ArtistImageRepositoryInterface {
        return ArtistImageRepository(db)
    }
}
