package com.android.autopay.data.models

data class SettingsData(
    val token: String,
    val isConnected: Boolean,
    val isAutomationEnabled: Boolean = true,
    val lastSuccessfulPingAt: Long = 0L
)
