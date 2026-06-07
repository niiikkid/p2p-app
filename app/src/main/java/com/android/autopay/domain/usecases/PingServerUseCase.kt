package com.android.autopay.domain.usecases

import com.android.autopay.BuildConfig
import com.android.autopay.domain.models.PingResult
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
        if (!settings.isConnected || settings.token.isBlank()) {
            return Result.success(Unit)
        }
        val pingResult: Result<PingResult> = notificationRepository.pingServer(settings.token)
        if (pingResult.isSuccess) {
            val serverAppVersion: String = pingResult.getOrThrow().androidAppVersion
            val isAppVersionSupported: Boolean = serverAppVersion == BuildConfig.APP_VERSION.trim()
            settingsRepository.saveAppVersionChecked(true)
            settingsRepository.saveAppVersionSupported(isAppVersionSupported)
            if (!isAppVersionSupported) {
                settingsRepository.saveAutomationEnabled(false)
                return Result.success(Unit)
            }
            settingsRepository.saveLastSuccessfulPingAt(System.currentTimeMillis())
        }
        return pingResult.map { }
    }
}
