package com.grabbit.di

import com.grabbit.data.search.MediaSearchProvider
import com.grabbit.data.search.YtDlpMediaSearchProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the platform search abstraction to its yt-dlp-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindMediaSearchProvider(provider: YtDlpMediaSearchProvider): MediaSearchProvider
}
