package com.android.autopay.presentation

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.SettingsData
import com.android.autopay.data.repositories.NotificationRepository
import com.android.autopay.data.utils.DEFAULT_URL
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val notificationRepository: NotificationRepository,
    @ApplicationContext context: Context
) : AndroidViewModel(context as Application) {

    private val _state = MutableStateFlow(MainContract.State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settingsData = dataStoreManager.getSettings().first()

            _state.value = state.value.copy(
                token = settingsData.token,
            )

            updateIsSavePossible()

            _state.value = state.value.copy(
                isConnected = true
            )
        }

        viewModelScope.launch {
            notificationRepository.observeHistory()
                .collect { notificationHistory ->
                    _state.value = state.value.copy(
                        notificationStats =
                        state.value.notificationStats.copy(
                            receiveAll = notificationHistory.size,
                            lastNotifications = notificationHistory.takeLast(5).reversed()
                        ),
                        allNotifications = notificationHistory
                    )
                }
        }

        viewModelScope.launch {
            val deviceName = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val androidVersion = Build.VERSION.RELEASE
            val apiLevel = Build.VERSION.SDK_INT
            val buildNumber = Build.DISPLAY
            val cpuAbi = Build.SUPPORTED_ABIS[0]

            _state.value = state.value.copy(
                deviceInfo = MainContract.State.DeviceInfo(
                    deviceName = deviceName,
                    manufacturer = manufacturer,
                    model = model,
                    androidVersion = androidVersion,
                    apiLevel = apiLevel.toString(),
                    buildNumber = buildNumber,
                    cpuAbi = cpuAbi
                )
            )
        }
    }

    fun onIntent(intent: MainContract.Intent) {
        when (intent) {
            is MainContract.Intent.ChangeToken -> onChangeToken(intent)
            is MainContract.Intent.Save -> onSave()
        }
    }

    private fun onChangeToken(intent: MainContract.Intent.ChangeToken) {
        _state.value = state.value.copy(token = intent.token)
        updateIsSavePossible()

        _state.value = state.value.copy(
            isConnected = false
        )
    }

    private fun updateIsSavePossible() {
        viewModelScope.launch {
            val settingsData = dataStoreManager.getSettings().first()
            _state.value = state.value.copy(
                isSavePossible = state.value.token != settingsData.token
            )
        }
    }

    private fun onSave() {
        viewModelScope.launch {
            dataStoreManager.saveSettings(
                SettingsData(
                    url = DEFAULT_URL,
                    token = state.value.token
                )
            )
            updateIsSavePossible()

            _state.value = state.value.copy(
                isConnected = true
            )
        }
    }
}

object MainContract {
    data class State(
        val token: String = "",
        val notificationStats: NotificationStats = NotificationStats(),
        val isSavePossible: Boolean = false,
        val isConnected: Boolean = false,
        val deviceInfo: DeviceInfo = DeviceInfo(),
        val allNotifications: List<Notification> = emptyList()
    ) {

        data class NotificationStats(
            val receiveAll: Int = 0,
            val lastNotifications: List<Notification> = emptyList()
        )

        data class DeviceInfo(
            val deviceName: String = "",
            val manufacturer: String = "",
            val model: String = "",
            val androidVersion: String = "",
            val apiLevel: String = "",
            val buildNumber: String = "",
            val cpuAbi: String = ""
        )
    }

    sealed class Intent {
        data class ChangeToken(val token: String) : Intent()
        data object Save : Intent()
    }
}