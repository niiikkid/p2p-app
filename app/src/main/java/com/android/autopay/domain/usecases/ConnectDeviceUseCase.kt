package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.SettingsData
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.domain.repository.SettingsRepository
import com.android.autopay.utils.SourceFilter
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ConnectDeviceUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(token: String): Result<Unit> {
        val connectResult: Result<Unit> = notificationRepository.connect(token)
        if (connectResult.isFailure) return connectResult
        val currentSettings: SettingsData = settingsRepository.getSettings().first()
        settingsRepository.saveSettings(
            SettingsData(
                token = token,
                isConnected = true,
                isAutomationEnabled = currentSettings.isAutomationEnabled,
                isAppVersionSupported = true,
                isAppVersionChecked = false,
                lastSuccessfulPingAt = currentSettings.lastSuccessfulPingAt,
                selectedSmsSenders = SourceFilter.normalizeSources(currentSettings.selectedSmsSenders),
                selectedBankApplications = SourceFilter.normalizeSources(currentSettings.selectedBankApplications),
                knownSmsSenders = SourceFilter.normalizeSources(currentSettings.knownSmsSenders),
                knownBankApplications = SourceFilter.normalizeSources(currentSettings.knownBankApplications),
            )
        )
        return Result.success(Unit)
    }
}
