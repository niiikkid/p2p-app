package com.android.autopay.data.local.mappers

import com.android.autopay.data.local.models.HistoryNotificationDBO
import com.android.autopay.data.local.models.UnsentNotificationDBO
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType

fun Notification.toHistoryNotificationDBO() = HistoryNotificationDBO(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = type.wireName,
    idempotencyKey = idempotencyKey,
    sentAt = sentAt
)

fun HistoryNotificationDBO.toNotification() = Notification(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = NotificationType.fromWireName(type),
    idempotencyKey = idempotencyKey,
    sentAt = sentAt,
    queuedForRetry = false
)

fun Notification.toUnsentNotificationDBO() = UnsentNotificationDBO(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = type.wireName,
    idempotencyKey = idempotencyKey
)

fun UnsentNotificationDBO.toNotification() = Notification(
    sender = sender,
    message = message,
    timestamp = timestamp,
    type = NotificationType.fromWireName(type),
    idempotencyKey = idempotencyKey,
    sentAt = 0L,
    queuedForRetry = true
)