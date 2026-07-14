package com.schwanitz.di

import com.schwanitz.data.rateLimit.RateLimiter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RateLimitModule {

    @Provides
    @Singleton
    @DiscogsRateLimiter
    fun provideDiscogsRateLimiter(): RateLimiter = RateLimiter(50)

    @Provides
    @Singleton
    @GeniusRateLimiter
    fun provideGeniusRateLimiter(): RateLimiter = RateLimiter(30)

    @Provides
    @Singleton
    @LastFmRateLimiter
    fun provideLastFmRateLimiter(): RateLimiter = RateLimiter(30)
}
