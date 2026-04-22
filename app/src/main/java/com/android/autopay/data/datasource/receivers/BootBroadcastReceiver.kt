package com.android.autopay.data.datasource.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.autopay.data.datasource.services.PushNotificationHandlerService
import com.android.autopay.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val isAutomationEnabled: Boolean = runBlocking {
            settingsRepository.getSettings().first().isAutomationEnabled
        }
        if (!isAutomationEnabled) return
        val serviceIntent = Intent(context, PushNotificationHandlerService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
