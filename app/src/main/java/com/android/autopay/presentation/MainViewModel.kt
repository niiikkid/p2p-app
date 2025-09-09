package com.android.autopay.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.SettingsData
import com.android.autopay.data.repositories.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _state: MutableStateFlow<MainContract.State> = MutableStateFlow(MainContract.State())
    val state: StateFlow<MainContract.State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settingsData = dataStoreManager.getSettings().first()
            _state.value = state.value.copy(
                token = settingsData.token,
                isConnected = settingsData.isConnected
            )
            updateIsSavePossible()
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
            _state.value = state.value.copy(isConnecting = true, errorMessage = null)
            val currentSettings = dataStoreManager.getSettings().first()
            val tokenToSave = state.value.token

            val connectResult = notificationRepository.connect(tokenToSave)
            if (connectResult.isSuccess) {
                dataStoreManager.saveSettings(
                    SettingsData(
                        url = currentSettings.url,
                        token = tokenToSave,
                        isConnected = true
                    )
                )
                _state.value = state.value.copy(isConnected = true, isConnecting = false)
            } else {
                _state.value = state.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    errorMessage = connectResult.exceptionOrNull()?.message
                )
            }
            updateIsSavePossible()
        }
    }
}

object MainContract {
    data class State(
        val token: String = "",
        val notificationStats: NotificationStats = NotificationStats(),
        val isSavePossible: Boolean = false,
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val errorMessage: String? = null,
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