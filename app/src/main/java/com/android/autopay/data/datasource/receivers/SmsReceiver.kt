package com.android.autopay.data.datasource.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationType
import com.android.autopay.domain.models.Sms
import com.android.autopay.domain.repository.SettingsRepository
import com.android.autopay.domain.usecases.SendNotificationUseCase
import com.android.autopay.utils.AppDispatchers
import com.android.autopay.utils.SourceFilter
import com.android.autopay.utils.StableId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sendNotificationUseCase: SendNotificationUseCase

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var appDispatchers: AppDispatchers

    private val scope by lazy { CoroutineScope(appDispatchers.io) }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val smsIntent: Intent = intent
        scope.launch {
            val settings = settingsRepository.getSettings().first()
            if (!settings.isAutomationEnabled || !settings.isAppVersionSupported) return@launch
            // This branch only handles real SMS broadcasts. Push notifications from bank apps are handled by NotificationListenerService.
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(smsIntent)
            if (messages.isEmpty()) {
                Log.d(TAG, "SMS broadcast skipped: no message segments")
                return@launch
            }
            val originatingAddress: String = messages[0].originatingAddress ?: UNKNOWN_SENDER
            val fullMessage: String = messages.joinToString(separator = "") { it.messageBody }
            Log.d(
                TAG,
                "Received raw SMS broadcast: sender=$originatingAddress, " +
                    "segments=${messages.size}, messageLength=${fullMessage.length}"
            )
            val sms = Sms(originatingAddress = originatingAddress, message = fullMessage)
            settingsRepository.addKnownSmsSender(sms.originatingAddress)
            if (!SourceFilter.isSmsAllowed(sms.originatingAddress, settings.selectedSmsSenders)) {
                Log.d(TAG, "SMS dropped by source filter: sender=${sms.originatingAddress}")
                return@launch
            }
            val stableKey: String = StableId.buildForSms(sender = sms.originatingAddress, message = sms.message)
            val notification = Notification(
                sender = sms.originatingAddress,
                message = sms.message,
                timestamp = System.currentTimeMillis(),
                type = NotificationType.SMS,
                idempotencyKey = stableKey,
            )
            Log.d(TAG, "Received SMS: sender=${notification.sender}")
            sendNotificationUseCase(notification)
                .onFailure { error -> Log.d(TAG, "SMS send failed: ${error.message}") }
        }
    }

    companion object {
        private const val TAG: String = "SmsReceiver"
        private const val UNKNOWN_SENDER: String = "Unknown"
    }
}
