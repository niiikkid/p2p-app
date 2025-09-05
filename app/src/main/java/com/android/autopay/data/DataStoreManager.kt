package com.android.autopay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.autopay.data.models.SettingsData
import com.android.autopay.data.utils.DEFAULT_URL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun saveSettings(settingsData: SettingsData) {
        context.dataStore.edit { settings ->
            settings[stringPreferencesKey(URL_KEY)] = settingsData.url
            settings[stringPreferencesKey(TOKEN_KEY)] = settingsData.token
        }
    }

    fun getSettings(): Flow<SettingsData> {
        return context.dataStore.data.map { preferences ->
            SettingsData(
                url = preferences[stringPreferencesKey(URL_KEY)] ?: DEFAULT_URL,
                token = preferences[stringPreferencesKey(TOKEN_KEY)] ?: ""
            )
        }
    }

    companion object {
        private const val URL_KEY = "url"
        private const val TOKEN_KEY = "token"
    }
}