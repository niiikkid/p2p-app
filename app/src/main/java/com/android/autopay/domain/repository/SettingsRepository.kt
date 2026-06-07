package com.android.autopay.domain.repository

import com.android.autopay.domain.models.SettingsData
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<SettingsData>
    suspend fun saveSettings(settingsData: SettingsData)
    suspend fun saveLastSuccessfulPingAt(timestampMs: Long)
    suspend fun saveAutomationEnabled(isEnabled: Boolean)
    suspend fun saveAppVersionSupported(isSupported: Boolean)
    suspend fun saveSelectedSmsSenders(senders: Set<String>)
    suspend fun saveSelectedBankApplications(packageNames: Set<String>)
    suspend fun addKnownSmsSender(sender: String)
    suspend fun addKnownBankApplication(packageName: String)
}
