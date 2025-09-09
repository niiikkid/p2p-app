package com.android.autopay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.autopay.data.models.SettingsData
import com.android.autopay.data.utils.DEFAULT_URL
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
            settings[stringPreferencesKey(URL_KEY)] = settingsData.url
            settings[stringPreferencesKey(TOKEN_KEY)] = settingsData.token
            settings[booleanPreferencesKey(IS_CONNECTED_KEY)] = settingsData.isConnected
        }
    }

    fun getSettings(): Flow<SettingsData> {
        return context.dataStore.data.map { preferences: Preferences ->
            val savedUrl: String = preferences[stringPreferencesKey(URL_KEY)] ?: DEFAULT_URL
            val savedToken: String = preferences[stringPreferencesKey(TOKEN_KEY)] ?: ""
            val savedIsConnected: Boolean = preferences[booleanPreferencesKey(IS_CONNECTED_KEY)] ?: false
            SettingsData(
                url = savedUrl,
                token = savedToken,
                isConnected = savedIsConnected
            )
        }
    }

    companion object {
        const val DATASTORE_NAME: String = "settings"
        private const val URL_KEY: String = "url"
        private const val TOKEN_KEY: String = "token"
        private const val IS_CONNECTED_KEY: String = "is_connected"
    }
}