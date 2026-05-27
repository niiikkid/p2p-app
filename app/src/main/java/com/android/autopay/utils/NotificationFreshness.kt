package com.android.autopay.utils

object NotificationFreshness {
    fun isExpired(eventTimestampMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (eventTimestampMs <= 0L) return false
        return nowMs - eventTimestampMs > NOTIFICATION_MAX_AGE_MS
    }
}
