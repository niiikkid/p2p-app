package com.android.autopay.domain.models

data class SettingsData(
    val token: String,
    val isConnected: Boolean,
    val isAutomationEnabled: Boolean = true,
    val isAppVersionSupported: Boolean = true,
    val isAppVersionChecked: Boolean = false,
    val lastSuccessfulPingAt: Long = 0L,
    val selectedSmsSenders: Set<String> = emptySet(),
    val selectedBankApplications: Set<String> = emptySet(),
    val knownSmsSenders: Set<String> = emptySet(),
    val knownBankApplications: Set<String> = emptySet()
)
