package com.android.autopay.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.autopay.R
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType
import com.android.autopay.data.models.Push
import com.android.autopay.data.repositories.NotificationRepository
import com.android.autopay.data.utils.AppDispatchers
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject


@AndroidEntryPoint
class PushNotificationHandlerService : NotificationListenerService() {

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var repository: NotificationRepository

    @Inject
    lateinit var appDispatchers: AppDispatchers

    private val scope by lazy { CoroutineScope(appDispatchers.io) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Получение уведомлений")
            .setContentText("Получение входящих Push-уведомлений")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        return START_STICKY
    }

    override fun onListenerConnected() {
        activeNotifications
        Log.d(TAG, "Listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn)
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val text = sbn.notification.extras.getString(TEXT_KEY)

        val notificationManager: NotificationManager =
            this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        if (packageName == Telephony.Sms.getDefaultSmsPackage(this)) return

        if (packageName == this.packageName) return

        val push = Push(packageName = packageName, message = text ?: "")

        val notification = Notification(
            sender = push.packageName,
            message = push.message,
            timestamp = System.currentTimeMillis(),
            type = NotificationType.PUSH,
            idempotencyKey = UUID.randomUUID().toString()
        )

        Log.d(TAG, "Получен пуш: $notification")

        scope.launch {
            repository.saveToHistory(notification)

            try {
                repository.sendToServer(notification)
                    .onFailure {
                        Log.d(
                            TAG,
                            "Не удалось отправить Push-уведомление на сервер. Добавляем в очередь на повтор: ${it.message}"
                        )

                        val notificationForRetry = notification.copy(
                            idempotencyKey = UUID.randomUUID().toString()
                        )
                        repository.saveForRetry(notificationForRetry)
                    }
            } catch (e: IOException) {
                Log.d(
                    TAG,
                    "Не удалось отправить Push-уведомление на сервер. Добавляем в очередь на повтор: ${e.message}",
                    e
                )
                repository.saveForRetry(notification)
            }
        }
    }

    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, PushNotificationHandlerService::class.java))
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val TAG = "NotificationHandlerService"
        private const val CHANNEL_ID = "NotificationHandlerService"
        private const val TEXT_KEY = "android.text"
    }
}