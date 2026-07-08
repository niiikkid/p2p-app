package com.android.autopay.data.datasource.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.android.autopay.data.datasource.services.PushNotificationHandlerService
import com.android.autopay.domain.models.SettingsData
import com.android.autopay.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Читаем настройки и запускаем сервис вне главного потока: чтение DataStore при
        // загрузке устройства может быть медленным из-за дисковой нагрузки и вызвать ANR,
        // если делать это через runBlocking в onReceive.
        val pendingResult: PendingResult = goAsync()
        val appContext: Context = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings: SettingsData = settingsRepository.getSettings().first()
                if (settings.isAutomationEnabled && settings.isAppVersionSupported && settings.isAppVersionChecked) {
                    startService(appContext)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Boot handling failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun startService(context: Context) {
        try {
            val serviceIntent = Intent(context, PushNotificationHandlerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to start service on boot: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG: String = "BootBroadcastReceiver"
    }
}
