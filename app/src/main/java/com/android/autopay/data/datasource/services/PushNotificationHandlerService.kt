package com.android.autopay.data.datasource.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.autopay.R
import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationType
import com.android.autopay.domain.models.Push
import com.android.autopay.domain.models.SettingsData
import com.android.autopay.domain.repository.SettingsRepository
import com.android.autopay.domain.usecases.PingServerUseCase
import com.android.autopay.domain.usecases.RetryPendingNotificationsUseCase
import com.android.autopay.domain.usecases.SendNotificationUseCase
import com.android.autopay.utils.AppDispatchers
import com.android.autopay.utils.FOREGROUND_NOTIFICATION_CHANNEL_ID
import com.android.autopay.utils.FOREGROUND_NOTIFICATION_ID
import com.android.autopay.utils.NOTIFICATION_TEXT_EXTRAS_KEY
import com.android.autopay.utils.NOTIFICATION_TITLE_EXTRAS_KEY
import com.android.autopay.utils.PING_INTERVAL_SECONDS
import com.android.autopay.utils.PUSH_MESSAGE_SEPARATOR
import com.android.autopay.utils.RETRY_INTERVAL_SECONDS
import com.android.autopay.utils.SourceFilter
import com.android.autopay.utils.StableId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PushNotificationHandlerService : NotificationListenerService() {

    @Inject
    lateinit var appDispatchers: AppDispatchers

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var sendNotificationUseCase: SendNotificationUseCase

    @Inject
    lateinit var retryPendingNotificationsUseCase: RetryPendingNotificationsUseCase

    @Inject
    lateinit var pingServerUseCase: PingServerUseCase

    private val scope by lazy { CoroutineScope(appDispatchers.io) }
    private var pingJob: Job? = null
    private var retryJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Получение уведомлений")
            .setContentText("Получение входящих Push-уведомлений")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
        startPingLoop()
        startRetryLoop()
        return START_STICKY
    }

    override fun onListenerConnected() {
        activeNotifications
        Log.d(TAG, "Listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            val settings = settingsRepository.getSettings().first()
            if (!settings.isAutomationEnabled) return@launch
            handleNotification(sbn, settings)
        }
    }

    private suspend fun handleNotification(sbn: StatusBarNotification, settings: SettingsData) {
        val packageName: String = sbn.packageName
        val text: String? = sbn.notification.extras.getString(NOTIFICATION_TEXT_EXTRAS_KEY)
        val title: String? = sbn.notification.extras.getString(NOTIFICATION_TITLE_EXTRAS_KEY)
        val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        if (packageName == Telephony.Sms.getDefaultSmsPackage(this)) return
        if (packageName == this.packageName) return
        settingsRepository.addKnownBankApplication(packageName)
        if (!SourceFilter.isBankApplicationAllowed(packageName, settings.selectedBankApplications)) {
            Log.d(TAG, "Push dropped by source filter: package=$packageName")
            return
        }
        val body: String = text?.trim().orEmpty()
        val header: String = title?.trim().orEmpty()
        val combinedMessage: String = when {
            header.isNotBlank() && body.isNotBlank() -> header + PUSH_MESSAGE_SEPARATOR + body
            body.isNotBlank() -> body
            else -> header
        }
        val push = Push(packageName = packageName, message = combinedMessage)
        val stableKey: String = StableId.buildForPush(packageName = push.packageName, combinedMessage = push.message)
        val notification = Notification(
            sender = push.packageName,
            message = push.message,
            timestamp = System.currentTimeMillis(),
            type = NotificationType.PUSH,
            idempotencyKey = stableKey,
        )
        Log.d(TAG, "Received push: sender=${notification.sender}")
        sendNotificationUseCase(notification)
            .onFailure { error -> Log.d(TAG, "Push send failed: ${error.message}") }
    }

    private fun startPingLoop() {
        if (pingJob?.isActive == true) return
        pingJob = scope.launch {
            while (isActive) {
                try {
                    pingServerUseCase()
                } catch (e: Exception) {
                    Log.d(TAG, "Ping loop error: ${e.message}", e)
                }
                delay(PING_INTERVAL_SECONDS * MILLIS_IN_SECOND)
            }
        }
    }

    private fun startRetryLoop() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (isActive) {
                try {
                    retryPendingNotificationsUseCase()
                } catch (e: Exception) {
                    Log.d(TAG, "Retry loop error: ${e.message}", e)
                }
                delay(RETRY_INTERVAL_SECONDS * MILLIS_IN_SECOND)
            }
        }
    }

    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, PushNotificationHandlerService::class.java))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartIntent = Intent(applicationContext, PushNotificationHandlerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.startForegroundService(restartIntent)
        } else {
            applicationContext.startService(restartIntent)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        pingJob?.cancel()
        retryJob?.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            FOREGROUND_NOTIFICATION_CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager: NotificationManager? = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val TAG: String = "NotificationHandlerService"
        private const val MILLIS_IN_SECOND: Long = 1000L
    }
}
