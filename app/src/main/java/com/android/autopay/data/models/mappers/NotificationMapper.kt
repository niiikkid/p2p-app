package com.android.autopay.data.models.mappers

import com.android.autopay.data.datasource.local.entities.HistoryNotificationDBO
import com.android.autopay.data.datasource.local.entities.UnsentNotificationDBO
import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationContentHiddenReason
import com.android.autopay.domain.models.NotificationContentStatus
import com.android.autopay.domain.models.NotificationType

fun Notification.toHistoryNotificationDBO() = HistoryNotificationDBO(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = type.wireName,
    idempotencyKey = idempotencyKey,
    contentStatus = contentStatus.wireName,
    hiddenReason = hiddenReason?.wireName,
    debugInfo = debugInfo,
    sentAt = sentAt
)

fun HistoryNotificationDBO.toNotification() = Notification(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = NotificationType.fromWireName(type),
    idempotencyKey = idempotencyKey,
    contentStatus = NotificationContentStatus.fromWireName(contentStatus),
    hiddenReason = NotificationContentHiddenReason.fromWireName(hiddenReason),
    debugInfo = debugInfo,
    sentAt = sentAt,
    queuedForRetry = false
)

fun Notification.toUnsentNotificationDBO() = UnsentNotificationDBO(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = type.wireName,
    idempotencyKey = idempotencyKey,
    contentStatus = contentStatus.wireName,
    hiddenReason = hiddenReason?.wireName,
    debugInfo = debugInfo
)

fun UnsentNotificationDBO.toNotification() = Notification(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = NotificationType.fromWireName(type),
    idempotencyKey = idempotencyKey,
    contentStatus = NotificationContentStatus.fromWireName(contentStatus),
    hiddenReason = NotificationContentHiddenReason.fromWireName(hiddenReason),
    debugInfo = debugInfo,
    sentAt = 0L,
    queuedForRetry = true
)
