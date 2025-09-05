package com.android.autopay.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unsent_notifications")
data class UnsentNotificationDBO(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val type: String,
    val idempotencyKey: String,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)