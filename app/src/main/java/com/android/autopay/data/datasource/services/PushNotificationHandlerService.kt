package com.android.autopay.data.datasource.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.android.autopay.R
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
import android.app.Notification as AndroidNotification
import com.android.autopay.domain.models.Notification as DomainNotification

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
    private val processedPushFingerprints: LinkedHashSet<String> = linkedSetOf()

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
        Log.d(TAG, "Listener connected")
        scope.launch {
            val settings: SettingsData = settingsRepository.getSettings().first()
            if (!settings.isAutomationEnabled) return@launch
            processActiveNotifications(settings)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processPostedNotification(sbn)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        processPostedNotification(sbn)
    }

    private fun processPostedNotification(sbn: StatusBarNotification) {
        scope.launch {
            val settings: SettingsData = settingsRepository.getSettings().first()
            if (!settings.isAutomationEnabled) return@launch
            val notifications: List<StatusBarNotification> = findNotificationsToHandle(sbn, getActiveNotificationsSafe())
            notifications.forEach { notification -> handleNotification(notification, settings) }
            cancelExternalNotifications(sbn)
        }
    }

    private suspend fun processActiveNotifications(settings: SettingsData) {
        val notifications: List<StatusBarNotification> = getActiveNotificationsSafe()
            .filterNot { notification -> notification.isGroupSummary() }
            .distinctBy { notification -> notification.key }
        notifications.forEach { notification -> handleNotification(notification, settings) }
        if (notifications.any { notification -> isExternalSource(notification.packageName) }) {
            cancelLocalNotifications()
        }
    }

    private fun findNotificationsToHandle(
        sbn: StatusBarNotification,
        activeNotifications: List<StatusBarNotification>
    ): List<StatusBarNotification> {
        if (!sbn.isGroupSummary()) return listOf(sbn)
        val relatedNotifications: List<StatusBarNotification> = activeNotifications
            .filter { candidate -> candidate.isRelatedTo(sbn) }
            .filterNot { candidate -> candidate.isGroupSummary() }
            .distinctBy { candidate -> candidate.key }
        if (relatedNotifications.isEmpty()) {
            Log.d(TAG, "Group summary skipped without child notifications: package=${sbn.packageName}")
        }
        return relatedNotifications
    }

    private suspend fun handleNotification(sbn: StatusBarNotification, settings: SettingsData) {
        val packageName: String = sbn.packageName
        if (packageName == Telephony.Sms.getDefaultSmsPackage(this)) return
        if (packageName == this.packageName) return
        settingsRepository.addKnownBankApplication(packageName)
        if (!SourceFilter.isBankApplicationAllowed(packageName, settings.selectedBankApplications)) {
            Log.d(TAG, "Push dropped by source filter: package=$packageName")
            return
        }
        if (sbn.isGroupSummary()) return
        val parsedNotification: ParsedPushNotification = parsePushNotification(sbn)
        val combinedMessage: String = parsedNotification.combinedMessage
        val push = Push(packageName = packageName, message = combinedMessage)
        val stableKey: String = StableId.buildForPush(packageName = push.packageName, combinedMessage = push.message)
        if (!rememberPushFingerprint(parsedNotification.fingerprint)) {
            Log.d(TAG, "Duplicate push callback skipped: package=$packageName")
            return
        }
        val notification = DomainNotification(
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

    private fun parsePushNotification(sbn: StatusBarNotification): ParsedPushNotification {
        val extras: Bundle = sbn.notification.extras
        val title: String = extras.getCharSequence(AndroidNotification.EXTRA_TITLE).toCleanString()
        val text: String = extras.getCharSequence(AndroidNotification.EXTRA_TEXT).toCleanString()
        val bigText: String = extras.getCharSequence(AndroidNotification.EXTRA_BIG_TEXT).toCleanString()
        val lines: List<String> = extras
            .getCharSequenceArray(AndroidNotification.EXTRA_TEXT_LINES)
            ?.mapNotNull { line -> line.toCleanString().takeIf { it.isNotBlank() } }
            .orEmpty()
        val messages: List<ParsedMessage> = extractMessagingStyleMessages(extras)
        val body: String = when {
            messages.isNotEmpty() -> messages.joinToString("\n") { message ->
                if (message.sender.isBlank()) message.text else "${message.sender}: ${message.text}"
            }
            lines.isNotEmpty() -> lines.joinToString("\n")
            bigText.isNotBlank() -> bigText
            else -> text
        }
        val combinedMessage: String = combineMessage(title = title, body = body)
        return ParsedPushNotification(
            combinedMessage = combinedMessage,
            fingerprint = buildCallbackFingerprint(
                packageName = sbn.packageName,
                title = title,
                text = combinedMessage,
                postTime = sbn.postTime,
                groupKey = sbn.groupKey
            )
        )
    }

    private fun extractMessagingStyleMessages(extras: Bundle): List<ParsedMessage> {
        @Suppress("DEPRECATION")
        val bundles = extras.getParcelableArray(AndroidNotification.EXTRA_MESSAGES) ?: return emptyList()
        return AndroidNotification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
            .orEmpty()
            .mapNotNull { message ->
                val text: String = message.text.toCleanString()
                if (text.isBlank()) return@mapNotNull null
                ParsedMessage(sender = getMessageSender(message), text = text)
            }
    }

    private fun getMessageSender(message: AndroidNotification.MessagingStyle.Message): String {
        val sender: CharSequence? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message.senderPerson?.name
        } else {
            @Suppress("DEPRECATION")
            message.sender
        }
        return sender.toCleanString()
    }

    private fun combineMessage(title: String, body: String): String {
        return when {
            title.isNotBlank() && body.isNotBlank() -> title + PUSH_MESSAGE_SEPARATOR + body
            body.isNotBlank() -> body
            else -> title
        }
    }

    private fun buildCallbackFingerprint(
        packageName: String,
        title: String,
        text: String,
        postTime: Long,
        groupKey: String?
    ): String {
        return listOf(packageName, title, text, postTime.toString(), groupKey.orEmpty()).joinToString("|")
    }

    private fun rememberPushFingerprint(fingerprint: String): Boolean {
        synchronized(processedPushFingerprints) {
            if (processedPushFingerprints.contains(fingerprint)) return false
            processedPushFingerprints.add(fingerprint)
            if (processedPushFingerprints.size > MAX_PROCESSED_FINGERPRINTS) {
                val oldestFingerprint: String = processedPushFingerprints.first()
                processedPushFingerprints.remove(oldestFingerprint)
            }
            return true
        }
    }

    private fun StatusBarNotification.isRelatedTo(source: StatusBarNotification): Boolean {
        val sourceGroupKey: String? = source.groupKey
        val sourceGroup: String? = source.notification.group
        val hasSameGroupKey: Boolean = !sourceGroupKey.isNullOrBlank() && groupKey == sourceGroupKey
        val hasSameNotificationGroup: Boolean = !sourceGroup.isNullOrBlank() && notification.group == sourceGroup
        return packageName == source.packageName &&
            (hasSameGroupKey || hasSameNotificationGroup)
    }

    private fun StatusBarNotification.isGroupSummary(): Boolean {
        return notification.flags and AndroidNotification.FLAG_GROUP_SUMMARY != 0
    }

    private fun CharSequence?.toCleanString(): String {
        return this?.toString()?.replace(NON_BREAKING_SPACE, " ")?.trim().orEmpty()
    }

    private fun getActiveNotificationsSafe(): List<StatusBarNotification> {
        return try {
            activeNotifications?.toList().orEmpty()
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read active notifications: ${e.message}", e)
            emptyList()
        }
    }

    private fun cancelExternalNotifications(sbn: StatusBarNotification) {
        if (!isExternalSource(sbn.packageName)) return
        cancelLocalNotifications()
    }

    private fun isExternalSource(packageName: String): Boolean {
        return packageName != Telephony.Sms.getDefaultSmsPackage(this) && packageName != this.packageName
    }

    private fun cancelLocalNotifications() {
        val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
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
        private const val MAX_PROCESSED_FINGERPRINTS: Int = 512
        private const val NON_BREAKING_SPACE: String = "\u00A0"
    }

    private data class ParsedPushNotification(
        val combinedMessage: String,
        val fingerprint: String,
    )

    private data class ParsedMessage(
        val sender: String,
        val text: String,
    )
}
