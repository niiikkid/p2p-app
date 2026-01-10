package com.android.autopay.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.autopay.data.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val dataStoreManager: DataStoreManager = DataStoreManager(context.applicationContext)
            val isAutomationEnabled: Boolean = runBlocking {
                dataStoreManager.getSettings().first().isAutomationEnabled
            }
            if (!isAutomationEnabled) return
            val serviceIntent = Intent(context, PushNotificationHandlerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}