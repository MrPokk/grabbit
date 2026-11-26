package com.grabbit.di

import com.grabbit.data.search.CompositeMediaSearchProvider
import com.grabbit.data.search.MediaSearchProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the platform search abstraction to the composite provider. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindMediaSearchProvider(provider: CompositeMediaSearchProvider): MediaSearchProvider
}
