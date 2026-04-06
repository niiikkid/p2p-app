package com.android.autopay.data.models

data class SettingsData(
    val token: String,
    val isConnected: Boolean,
    val isAutomationEnabled: Boolean = true,
    val lastSuccessfulPingAt: Long = 0L,
    val selectedSmsSenders: Set<String> = emptySet(),
    val selectedBankApplications: Set<String> = emptySet(),
    val knownSmsSenders: Set<String> = emptySet(),
    val knownBankApplications: Set<String> = emptySet()
)
