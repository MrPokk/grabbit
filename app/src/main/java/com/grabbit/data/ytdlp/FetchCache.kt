package com.grabbit.data.ytdlp

import android.content.Context
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
import com.grabbit.core.Cache
import kotlinx.serialization.json.Json

class FetchMemoryCache(
    private val maxSize: Int = Cache.FETCH_MEMORY_ENTRIES,
    private val ttlMs: Long = Cache.FETCH_TTL.inWholeMilliseconds,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val media: FetchedMedia,
        val savedAtMs: Long,
    )

    // accessOrder = true makes the map iterate from the least recently used entry.
    private val cache = LinkedHashMap<String, Entry>(maxSize, LOAD_FACTOR, true)

    fun get(key: String): FetchedMedia? =
        synchronized(cache) {
            val entry = cache[key] ?: return null
            if (clock() - entry.savedAtMs > ttlMs) {
                cache.remove(key)
                return null
            }
            entry.media
        }

    fun put(
        key: String,
        value: FetchedMedia,
    ) {
        synchronized(cache) {
            cache[key] = Entry(value, clock())
            while (cache.size > maxSize) {
                val eldest = cache.keys.firstOrNull() ?: break
                cache.remove(eldest)
            }
        }
    }

    fun remove(key: String) {
        synchronized(cache) { cache.remove(key) }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}

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
