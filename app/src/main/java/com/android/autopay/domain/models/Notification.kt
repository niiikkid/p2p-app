package com.android.autopay.domain.models

data class Notification(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val type: NotificationType,
    val idempotencyKey: String,
    val contentStatus: NotificationContentStatus = NotificationContentStatus.VISIBLE,
    val hiddenReason: NotificationContentHiddenReason? = null,
    val debugInfo: String? = null,
    val sentAt: Long = 0L,
    val queuedForRetry: Boolean = false,
)
