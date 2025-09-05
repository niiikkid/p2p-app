package com.android.autopay.data.models

data class Notification(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val type: String,
    val idempotencyKey: String,
)