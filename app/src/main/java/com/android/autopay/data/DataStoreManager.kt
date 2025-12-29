package com.android.autopay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.autopay.data.models.SettingsData
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
            settings[longPreferencesKey(LAST_SUCCESSFUL_PING_AT_KEY)] = settingsData.lastSuccessfulPingAt
        }
    }

    fun getSettings(): Flow<SettingsData> {
        return context.dataStore.data.map { preferences: Preferences ->
            val savedToken: String = preferences[stringPreferencesKey(TOKEN_KEY)] ?: ""
            val savedIsConnected: Boolean = preferences[booleanPreferencesKey(IS_CONNECTED_KEY)] ?: false
            val savedLastSuccessfulPingAt: Long = preferences[longPreferencesKey(LAST_SUCCESSFUL_PING_AT_KEY)] ?: 0L
            SettingsData(
                token = savedToken,
                isConnected = savedIsConnected,
                lastSuccessfulPingAt = savedLastSuccessfulPingAt
            )
        }
    }

    suspend fun saveLastSuccessfulPingAt(timestampMs: Long): Unit {
        context.dataStore.edit { settings ->
            settings[longPreferencesKey(LAST_SUCCESSFUL_PING_AT_KEY)] = timestampMs
        }
    }

    companion object {
        const val DATASTORE_NAME: String = "settings"
        private const val TOKEN_KEY: String = "token"
        private const val IS_CONNECTED_KEY: String = "is_connected"
        private const val LAST_SUCCESSFUL_PING_AT_KEY: String = "last_successful_ping_at"
    }
}