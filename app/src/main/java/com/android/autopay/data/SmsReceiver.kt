package com.android.autopay.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType
import com.android.autopay.data.models.Sms
import com.android.autopay.data.repositories.NotificationRepository
import com.android.autopay.data.utils.AppDispatchers
import com.android.autopay.data.utils.StableId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import org.json.JSONObject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: NotificationRepository

    @Inject
    lateinit var appDispatchers: AppDispatchers

    private val scope by lazy { CoroutineScope(appDispatchers.io) }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val originatingAddress = messages[0].originatingAddress
            messages[0]

            val fullMessage = messages.joinToString(separator = "") { it.messageBody }

            val sms = Sms(
                originatingAddress = originatingAddress ?: "Unknown",
                message = fullMessage
            )

            val stableKey: String = StableId.buildForSms(sender = sms.originatingAddress, message = sms.message)

            val notification = Notification(
                sender = sms.originatingAddress,
                message = sms.message,
                timestamp = System.currentTimeMillis(),
                type = NotificationType.SMS,
                idempotencyKey = stableKey
            )

            Log.d(TAG, "Получено смс: sender=${notification.sender}, message=${notification.message}")

            scope.launch {
                repository.saveToHistory(notification)

                val json = JSONObject().apply {
                    put("sender", notification.sender)
                    put("message", notification.message)
                    put("timestamp", notification.timestamp)
                    put("type", notification.type.wireName)
                }
                Log.d(TAG, "HTTP -> Тело запроса: ${json}")

                try {
                    repository.sendToServer(notification)
                        .onSuccess {
                            Log.d(TAG, "HTTP <- Успешный ответ от сервера (см. тело в перехватчике)")
                            repository.markSentSuccess(notification)
                        }
                        .onFailure {
                            Log.d(
                                TAG,
                                "Не удалось отправить СМС на сервер. Добавляем в очередь на повтор: ${it.message}",
                            )

                            repository.saveForRetry(notification)
                        }
                } catch (e: IOException) {
                    Log.d(
                        TAG,
                        "Не удалось отправить СМС на сервер. Добавляем в очередь на повтор: ${e.message}",
                        e
                    )
                    repository.saveForRetry(notification)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}