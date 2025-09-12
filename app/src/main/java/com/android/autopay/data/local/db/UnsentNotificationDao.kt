package com.android.autopay.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.autopay.data.local.models.UnsentNotificationDBO
import kotlinx.coroutines.flow.Flow

@Dao
interface UnsentNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: UnsentNotificationDBO)

    @Delete
    suspend fun delete(notification: UnsentNotificationDBO)

    @Query("SELECT * FROM unsent_notifications")
    suspend fun getAll(): List<UnsentNotificationDBO>

    @Query("SELECT EXISTS(SELECT 1 FROM unsent_notifications WHERE idempotencyKey = :key LIMIT 1)")
    suspend fun existsByIdempotencyKey(key: String): Boolean

    @Query("SELECT COUNT(*) FROM unsent_notifications")
    fun observeCount(): Flow<Int>
}