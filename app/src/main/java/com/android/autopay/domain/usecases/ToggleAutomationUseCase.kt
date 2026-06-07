package com.android.autopay.domain.usecases

import com.android.autopay.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ToggleAutomationUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(): Boolean {
        val currentSettings = settingsRepository.getSettings().first()
        if (!currentSettings.isAppVersionSupported || !currentSettings.isAppVersionChecked) {
            settingsRepository.saveAutomationEnabled(false)
            return false
        }
        val targetState: Boolean = !currentSettings.isAutomationEnabled
        settingsRepository.saveAutomationEnabled(targetState)
        return targetState
    }
}
