package com.android.autopay.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.autopay.data.local.models.HistoryNotificationDBO
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: HistoryNotificationDBO)

    @Query("SELECT * FROM notification_history")
    fun observeAll(): Flow<List<HistoryNotificationDBO>>

    @Query("SELECT COUNT(*) FROM notification_history")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<HistoryNotificationDBO>>

    @Query(
        "SELECT * FROM notification_history " +
            "WHERE (:pattern IS NULL OR LOWER(sender) LIKE LOWER(:pattern) ESCAPE '\\' " +
            "OR LOWER(message) LIKE LOWER(:pattern) ESCAPE '\\' " +
            "OR LOWER(type) LIKE LOWER(:pattern) ESCAPE '\\') " +
            "ORDER BY timestamp DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getPage(pattern: String?, limit: Int, offset: Int): List<HistoryNotificationDBO>
}