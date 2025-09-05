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
}