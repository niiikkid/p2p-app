package com.android.autopay.data.local.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_history",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class HistoryNotificationDBO(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val type: String,
    val idempotencyKey: String,
    val sentAt: Long = 0L,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)