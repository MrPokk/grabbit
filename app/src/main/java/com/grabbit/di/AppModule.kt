package com.grabbit.di

import android.content.Context
import androidx.room.Room
import com.grabbit.data.db.AppDatabase
import com.grabbit.data.db.HistoryDao
import com.grabbit.data.db.Migrations
import com.grabbit.data.ytdlp.FetchCacheDatabase
import com.grabbit.data.ytdlp.FetchCacheDao
import com.grabbit.data.ytdlp.FetchMemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Room database and its DAO to the whole app. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Builds the history database with explicit migrations so saved downloads survive updates. */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "grabbit.db")
            .apply { Migrations.ALL.forEach { migration -> addMigrations(migration) } }
            .build()

    /** Exposes the history DAO. */
    @Provides
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()

    /** In-memory LRU cache for fetch results. */
    @Provides
    @Singleton
    fun provideFetchMemoryCache(): FetchMemoryCache = FetchMemoryCache()

    /** Persistent fetch cache database. */
    @Provides
    @Singleton
    fun provideFetchCacheDatabase(@ApplicationContext context: Context): FetchCacheDatabase =
        FetchCacheDatabase.getInstance(context)

    /** Exposes the fetch cache DAO. */
    @Provides
    fun provideFetchCacheDao(database: FetchCacheDatabase): FetchCacheDao = database.fetchCacheDao()
}
