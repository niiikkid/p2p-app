package com.android.autopay.data.datasource.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unsent_notifications",
    indices = [Index(value = ["idempotencyKey"], unique = true)]
)
data class UnsentNotificationDBO(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val type: String,
    val idempotencyKey: String,
    val contentStatus: String = "visible",
    val hiddenReason: String? = null,
    val debugInfo: String? = null,
    val lastRetryAt: Long = 0L,
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0
)
