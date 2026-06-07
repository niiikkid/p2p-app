package com.android.autopay.data.repository

import com.android.autopay.data.datasource.preferences.DataStoreManager
import com.android.autopay.domain.models.SettingsData
import com.android.autopay.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : SettingsRepository {

    override fun getSettings(): Flow<SettingsData> = dataStoreManager.getSettings()

    override suspend fun saveSettings(settingsData: SettingsData) {
        dataStoreManager.saveSettings(settingsData)
    }

    override suspend fun saveLastSuccessfulPingAt(timestampMs: Long) {
        dataStoreManager.saveLastSuccessfulPingAt(timestampMs)
    }

    override suspend fun saveAutomationEnabled(isEnabled: Boolean) {
        dataStoreManager.saveAutomationEnabled(isEnabled)
    }

    override suspend fun saveAppVersionSupported(isSupported: Boolean) {
        dataStoreManager.saveAppVersionSupported(isSupported)
    }

    override suspend fun saveSelectedSmsSenders(senders: Set<String>) {
        dataStoreManager.saveSelectedSmsSenders(senders)
    }

    override suspend fun saveSelectedBankApplications(packageNames: Set<String>) {
        dataStoreManager.saveSelectedBankApplications(packageNames)
    }

    override suspend fun addKnownSmsSender(sender: String) {
        dataStoreManager.addKnownSmsSender(sender)
    }

    override suspend fun addKnownBankApplication(packageName: String) {
        dataStoreManager.addKnownBankApplication(packageName)
    }
}
