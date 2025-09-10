package com.android.autopay.data.models

data class SettingsData(
    val url: String,
    val token: String,
    val isConnected: Boolean,
    val lastSuccessfulPingAt: Long = 0L,
)
