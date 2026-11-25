package com.grabbit.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One saved download in the history list. */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val mediaType: String,
    val formatLabel: String,
    val sizeBytes: Long,
    val durationSeconds: Long,
    val createdAt: Long,
    val sourceSite: String?,
    val optionsJson: String,
)

/** Read/write access to saved downloads; [observe] filters by title, uploader or file name. */
@Dao
interface HistoryDao {
    @Query(
        """
        SELECT * FROM history
        WHERE title LIKE '%' || :query || '%'
           OR uploader LIKE '%' || :query || '%'
           OR fileName LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """,
    )
    fun observe(query: String): Flow<List<HistoryEntity>>

    /** All entries, newest first. */
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    /** Looks up one saved download by id. */
    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun find(id: Long): HistoryEntity?

    /** Saves a finished download and returns its row id. */
    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    /** Removes one history entry. */
    @Delete
    suspend fun delete(entity: HistoryEntity)

    /** Removes every history entry. */
    @Query("DELETE FROM history")
    suspend fun clear()
}

/** Room holder for the history table; schema changes require an explicit migration. */
@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    /** DAO used by the history screen and the download engine. */
    abstract fun historyDao(): HistoryDao
}
