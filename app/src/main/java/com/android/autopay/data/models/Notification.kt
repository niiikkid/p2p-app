package com.android.autopay.data.models

data class Notification(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val type: NotificationType,
    val idempotencyKey: String,
    val sentAt: Long = 0L,
    val queuedForRetry: Boolean = false,
)