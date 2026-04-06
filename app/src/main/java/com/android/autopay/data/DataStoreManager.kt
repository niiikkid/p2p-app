package com.android.autopay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.autopay.data.models.SettingsData
import com.android.autopay.data.utils.SourceFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DataStoreManager.DATASTORE_NAME)

class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun saveSettings(settingsData: SettingsData): Unit {
        context.dataStore.edit { settings ->
            settings[stringPreferencesKey(TOKEN_KEY)] = settingsData.token
            settings[booleanPreferencesKey(IS_CONNECTED_KEY)] = settingsData.isConnected
            settings[booleanPreferencesKey(IS_AUTOMATION_ENABLED_KEY)] = settingsData.isAutomationEnabled
            settings[longPreferencesKey(LAST_SUCCESSFUL_PING_AT_KEY)] = settingsData.lastSuccessfulPingAt
            settings[stringSetPreferencesKey(SELECTED_SMS_SENDERS_KEY)] = settingsData.selectedSmsSenders
            settings[stringSetPreferencesKey(SELECTED_BANK_APPLICATIONS_KEY)] = settingsData.selectedBankApplications
            settings[stringSetPreferencesKey(KNOWN_SMS_SENDERS_KEY)] = settingsData.knownSmsSenders
            settings[stringSetPreferencesKey(KNOWN_BANK_APPLICATIONS_KEY)] = settingsData.knownBankApplications
        }
    }

    fun getSettings(): Flow<SettingsData> {
        return context.dataStore.data.map { preferences: Preferences ->
            val savedToken: String = preferences[stringPreferencesKey(TOKEN_KEY)] ?: ""
            val savedIsConnected: Boolean = preferences[booleanPreferencesKey(IS_CONNECTED_KEY)] ?: false
            val savedIsAutomationEnabled: Boolean = preferences[booleanPreferencesKey(IS_AUTOMATION_ENABLED_KEY)] ?: true
            val savedLastSuccessfulPingAt: Long = preferences[longPreferencesKey(LAST_SUCCESSFUL_PING_AT_KEY)] ?: 0L
            val savedSelectedSmsSenders: Set<String> = preferences[stringSetPreferencesKey(SELECTED_SMS_SENDERS_KEY)] ?: emptySet()
            val savedSelectedBankApplications: Set<String> = preferences[stringSetPreferencesKey(SELECTED_BANK_APPLICATIONS_KEY)] ?: emptySet()
            val savedKnownSmsSenders: Set<String> = preferences[stringSetPreferencesKey(KNOWN_SMS_SENDERS_KEY)] ?: emptySet()
            val savedKnownBankApplications: Set<String> = preferences[stringSetPreferencesKey(KNOWN_BANK_APPLICATIONS_KEY)] ?: emptySet()
            SettingsData(
                token = savedToken,
                isConnected = savedIsConnected,
                isAutomationEnabled = savedIsAutomationEnabled,
                lastSuccessfulPingAt = savedLastSuccessfulPingAt,
                selectedSmsSenders = savedSelectedSmsSenders,
                selectedBankApplications = savedSelectedBankApplications,
                knownSmsSenders = savedKnownSmsSenders,
                knownBankApplications = savedKnownBankApplications
            )
        }
    }

    suspend fun saveLastSuccessfulPingAt(timestampMs: Long): Unit {
        context.dataStore.edit { settings ->
            settings[longPreferencesKey(LAST_SUCCESSFUL_PING_AT_KEY)] = timestampMs
        }
    }

    suspend fun saveAutomationEnabled(isEnabled: Boolean): Unit {
        context.dataStore.edit { settings ->
            settings[booleanPreferencesKey(IS_AUTOMATION_ENABLED_KEY)] = isEnabled
        }
    }
    suspend fun saveSelectedSmsSenders(senders: Set<String>): Unit {
        context.dataStore.edit { settings ->
            settings[stringSetPreferencesKey(SELECTED_SMS_SENDERS_KEY)] = senders
        }
    }
    suspend fun saveSelectedBankApplications(packageNames: Set<String>): Unit {
        context.dataStore.edit { settings ->
            settings[stringSetPreferencesKey(SELECTED_BANK_APPLICATIONS_KEY)] = packageNames
        }
    }
    suspend fun addKnownSmsSender(sender: String): Unit {
        val normalizedSender: String = SourceFilter.normalizeSource(sender)
        if (normalizedSender.isBlank()) return
        context.dataStore.edit { settings ->
            val previousSenders: Set<String> = settings[stringSetPreferencesKey(KNOWN_SMS_SENDERS_KEY)] ?: emptySet()
            settings[stringSetPreferencesKey(KNOWN_SMS_SENDERS_KEY)] = previousSenders + normalizedSender
        }
    }
    suspend fun addKnownBankApplication(packageName: String): Unit {
        val normalizedPackageName: String = SourceFilter.normalizeSource(packageName)
        if (normalizedPackageName.isBlank()) return
        context.dataStore.edit { settings ->
            val previousPackages: Set<String> = settings[stringSetPreferencesKey(KNOWN_BANK_APPLICATIONS_KEY)] ?: emptySet()
            settings[stringSetPreferencesKey(KNOWN_BANK_APPLICATIONS_KEY)] = previousPackages + normalizedPackageName
        }
    }

    companion object {
        const val DATASTORE_NAME: String = "settings"
        private const val TOKEN_KEY: String = "token"
        private const val IS_CONNECTED_KEY: String = "is_connected"
        private const val IS_AUTOMATION_ENABLED_KEY: String = "is_automation_enabled"
        private const val LAST_SUCCESSFUL_PING_AT_KEY: String = "last_successful_ping_at"
        private const val SELECTED_SMS_SENDERS_KEY: String = "selected_sms_senders"
        private const val SELECTED_BANK_APPLICATIONS_KEY: String = "selected_bank_applications"
        private const val KNOWN_SMS_SENDERS_KEY: String = "known_sms_senders"
        private const val KNOWN_BANK_APPLICATIONS_KEY: String = "known_bank_applications"
    }
}