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
import com.android.autopay.data.utils.FOREGROUND_NOTIFICATION_CHANNEL_ID
import com.android.autopay.data.utils.FOREGROUND_NOTIFICATION_ID
import com.android.autopay.data.utils.NOTIFICATION_TEXT_EXTRAS_KEY
import com.android.autopay.data.utils.NOTIFICATION_TITLE_EXTRAS_KEY
import com.android.autopay.data.utils.PUSH_MESSAGE_SEPARATOR
import com.android.autopay.data.utils.PING_ENDPOINT_PATH
import com.android.autopay.data.utils.PING_INTERVAL_SECONDS
import com.android.autopay.data.DataStoreManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request


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
    private var pingJob: Job? = null
    @Inject lateinit var httpClient: OkHttpClient
    @Inject lateinit var dataStoreManager: DataStoreManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Получение уведомлений")
            .setContentText("Получение входящих Push-уведомлений")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        startPingLoop()
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
        val text = sbn.notification.extras.getString(NOTIFICATION_TEXT_EXTRAS_KEY)
        val title = sbn.notification.extras.getString(NOTIFICATION_TITLE_EXTRAS_KEY)

        val notificationManager: NotificationManager =
            this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        if (packageName == Telephony.Sms.getDefaultSmsPackage(this)) return

        if (packageName == this.packageName) return

        val body: String = text?.trim().orEmpty()
        val header: String = title?.trim().orEmpty()
        val combinedMessage: String = if (header.isNotBlank() && body.isNotBlank()) {
            header + PUSH_MESSAGE_SEPARATOR + body
        } else if (body.isNotBlank()) {
            body
        } else {
            header
        }

        val push = Push(packageName = packageName, message = combinedMessage)

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

    private fun startPingLoop() {
        if (pingJob?.isActive == true) return
        pingJob = scope.launch {
            while (isActive) {
                try { performPing() } catch (_: Exception) {}
                delay(PING_INTERVAL_SECONDS * 1000L)
            }
        }
    }

    private suspend fun performPing() {
        val settings = dataStoreManager.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank()) return
        val pingUrl: String = buildPingUrl(settings.url)
        val headers = Headers.Builder().add("Accept", "application/json").add("Access-Token", settings.token).build()
        val request = Request.Builder().url(pingUrl).headers(headers).get().build()
        withContext(appDispatchers.io) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        dataStoreManager.saveLastSuccessfulPingAt(System.currentTimeMillis())
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ping failed: ${e.message}", e)
            }
        }
    }

    private fun buildPingUrl(settingsUrl: String): String {
        val apiSmsSegment: String = "/api/app/sms"
        return if (settingsUrl.contains(apiSmsSegment)) settingsUrl.replace(apiSmsSegment, PING_ENDPOINT_PATH) else {
            val base: String = if (settingsUrl.endsWith("/")) settingsUrl.dropLast(1) else settingsUrl
            "$base$PING_ENDPOINT_PATH"
        }
    }

    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, PushNotificationHandlerService::class.java))
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            FOREGROUND_NOTIFICATION_CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    companion object {
        private const val TAG = "NotificationHandlerService"
    }
}