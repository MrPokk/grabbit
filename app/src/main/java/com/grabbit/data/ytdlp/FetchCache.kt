package com.grabbit.data.ytdlp

import android.content.Context
import android.util.LruCache
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.grabbit.core.AppLog
import kotlinx.serialization.json.Json

/** In-memory LRU cache for fetch results. */
class FetchMemoryCache(
    private val maxSize: Int = 20,
) {
    private val cache =
        object : LruCache<String, FetchedMedia>(maxSize) {
            override fun sizeOf(
                key: String,
                value: FetchedMedia,
            ): Int = 1
        }

    fun get(key: String): FetchedMedia? = cache.get(key)

    fun put(
        key: String,
        value: FetchedMedia,
    ) {
        cache.put(key, value)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }
}

/** Room entity for persistent fetch cache. */
@Entity(tableName = "fetch_cache")
data class FetchCacheEntity(
    @PrimaryKey val normalizedUrl: String,
    val json: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Dao
interface FetchCacheDao {
    @Query("SELECT * FROM fetch_cache WHERE normalizedUrl = :normalizedUrl")
    suspend fun get(normalizedUrl: String): FetchCacheEntity?

    @Query("SELECT * FROM fetch_cache WHERE normalizedUrl = :normalizedUrl AND timestamp > :minTimestamp")
    suspend fun getFresh(
        normalizedUrl: String,
        minTimestamp: Long,
    ): FetchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: FetchCacheEntity)

    @Query("DELETE FROM fetch_cache WHERE timestamp < :maxAge")
    suspend fun evictOlderThan(maxAge: Long)

    @Query("DELETE FROM fetch_cache")
    suspend fun clear()
}

@Database(entities = [FetchCacheEntity::class], version = 1, exportSchema = false)
abstract class FetchCacheDatabase : RoomDatabase() {
    abstract fun fetchCacheDao(): FetchCacheDao

    companion object {
        @Volatile private var instance: FetchCacheDatabase? = null

        fun getInstance(context: Context): FetchCacheDatabase =
            instance ?: synchronized(this) {
                val created =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            FetchCacheDatabase::class.java,
                            "fetch_cache.db",
                        ).build()
                instance = created
                created
            }
    }
}

/** Serialization helper for FetchedMedia. */
object FetchCacheSerializer {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    fun toJson(media: FetchedMedia): String = json.encodeToString(media)

    fun fromJson(jsonString: String): FetchedMedia? =
        try {
            json.decodeFromString<FetchedMedia>(jsonString)
        } catch (e: Exception) {
            AppLog.e("FetchCacheSerializer", "Failed to deserialize fetch cache", e)
            null
        }
}
