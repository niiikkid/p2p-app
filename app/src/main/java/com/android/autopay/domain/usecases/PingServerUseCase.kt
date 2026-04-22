package com.android.autopay.domain.usecases

import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PingServerUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(): Result<Unit> {
        val settings = settingsRepository.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank() || !settings.isAutomationEnabled) {
            return Result.success(Unit)
        }
        val pingResult: Result<Unit> = notificationRepository.pingServer(settings.token)
        if (pingResult.isSuccess) {
            settingsRepository.saveLastSuccessfulPingAt(System.currentTimeMillis())
        }
        return pingResult
    }
}
