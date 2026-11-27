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

    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun find(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Delete
    suspend fun delete(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

// Schema changes require an explicit Room Migration; never destructive.
@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
