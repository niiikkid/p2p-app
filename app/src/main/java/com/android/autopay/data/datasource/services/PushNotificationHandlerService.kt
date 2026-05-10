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
import com.android.autopay.BuildConfig
import com.android.autopay.R
import com.android.autopay.domain.models.NotificationContentHiddenReason
import com.android.autopay.domain.models.NotificationContentStatus
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
        logNotificationDiagnostics(sbn)
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
            contentStatus = parsedNotification.contentStatus,
            hiddenReason = parsedNotification.hiddenReason,
            debugInfo = buildNotificationDebugInfo(sbn),
        )
        Log.d(
            TAG,
            "Received push: sender=${notification.sender}, " +
                "contentStatus=${notification.contentStatus.wireName}, " +
                "reason=${notification.hiddenReason?.wireName.orEmpty()}"
        )
        sendNotificationUseCase(notification)
            .onFailure { error -> Log.d(TAG, "Push send failed: ${error.message}") }
    }

    private fun parsePushNotification(sbn: StatusBarNotification): ParsedPushNotification {
        val content: NotificationTextSnapshot = extractNotificationTextSnapshot(sbn.notification.extras)
        val hiddenClassification: HiddenContentClassification = classifyHiddenContent(
            notification = sbn.notification,
            primaryContent = content,
            publicContent = sbn.notification.publicVersion?.extras?.let { publicExtras ->
                extractNotificationTextSnapshot(publicExtras)
            }
        )
        val body: String = when {
            content.messages.isNotEmpty() -> content.messages.joinToString("\n") { message ->
                if (message.sender.isBlank()) message.text else "${message.sender}: ${message.text}"
            }
            content.lines.isNotEmpty() -> content.lines.joinToString("\n")
            content.bigText.isNotBlank() -> content.bigText
            else -> content.text
        }
        val extractedMessage: String = combineMessage(title = content.title, body = body)
        val combinedMessage: String = if (hiddenClassification.contentStatus == NotificationContentStatus.HIDDEN_BY_ANDROID_OR_APP) {
            HIDDEN_NOTIFICATION_FALLBACK_MESSAGE
        } else {
            extractedMessage
        }
        return ParsedPushNotification(
            combinedMessage = combinedMessage,
            fingerprint = buildCallbackFingerprint(
                packageName = sbn.packageName,
                title = content.title,
                text = combinedMessage,
                postTime = sbn.postTime,
                groupKey = sbn.groupKey
            ),
            contentStatus = hiddenClassification.contentStatus,
            hiddenReason = hiddenClassification.reason
        )
    }

    private fun extractNotificationTextSnapshot(extras: Bundle): NotificationTextSnapshot {
        val messages: List<ParsedMessage> = extractMessagingStyleMessages(extras)
        return NotificationTextSnapshot(
            title = extras.getCharSequence(AndroidNotification.EXTRA_TITLE).toCleanString(),
            text = extras.getCharSequence(AndroidNotification.EXTRA_TEXT).toCleanString(),
            bigText = extras.getCharSequence(AndroidNotification.EXTRA_BIG_TEXT).toCleanString(),
            subText = extras.getCharSequence(AndroidNotification.EXTRA_SUB_TEXT).toCleanString(),
            summaryText = extras.getCharSequence(AndroidNotification.EXTRA_SUMMARY_TEXT).toCleanString(),
            infoText = extras.getCharSequence(AndroidNotification.EXTRA_INFO_TEXT).toCleanString(),
            lines = extras
                .getCharSequenceArray(AndroidNotification.EXTRA_TEXT_LINES)
                ?.mapNotNull { line -> line.toCleanString().takeIf { it.isNotBlank() } }
                .orEmpty(),
            messages = messages
        )
    }

    private fun classifyHiddenContent(
        notification: AndroidNotification,
        primaryContent: NotificationTextSnapshot,
        publicContent: NotificationTextSnapshot?
    ): HiddenContentClassification {
        if (primaryContent.hasRealNotificationText()) return HiddenContentClassification.visible()
        val hasPrimaryText: Boolean = primaryContent.hasAnyText()
        val hasPublicText: Boolean = publicContent?.hasAnyText() == true
        val reason: NotificationContentHiddenReason = when {
            notification.visibility == AndroidNotification.VISIBILITY_SECRET -> NotificationContentHiddenReason.VISIBILITY_SECRET
            !hasPrimaryText && notification.publicVersion != null && hasPublicText -> NotificationContentHiddenReason.PUBLIC_VERSION_ONLY
            notification.visibility == AndroidNotification.VISIBILITY_PRIVATE -> NotificationContentHiddenReason.VISIBILITY_PRIVATE
            !hasPrimaryText -> NotificationContentHiddenReason.EMPTY_EXTRAS
            else -> NotificationContentHiddenReason.UNKNOWN
        }
        // Если Android Notification API не содержит реального текста, приложение не может легально восстановить его.
        return HiddenContentClassification(
            contentStatus = NotificationContentStatus.HIDDEN_BY_ANDROID_OR_APP,
            reason = reason
        )
    }

    private fun extractMessagingStyleMessages(extras: Bundle): List<ParsedMessage> {
        return try {
            @Suppress("DEPRECATION")
            val bundles = extras.getParcelableArray(AndroidNotification.EXTRA_MESSAGES) ?: return emptyList()
            AndroidNotification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
                .orEmpty()
                .mapNotNull { message ->
                    val text: String = message.text.toCleanString()
                    if (text.isBlank()) return@mapNotNull null
                    ParsedMessage(sender = getMessageSender(message), text = text)
                }
        } catch (e: Exception) {
            Log.d(TAG, "Unable to parse notification messaging style extras: ${e.message}", e)
            emptyList()
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

    private fun logNotificationDiagnostics(sbn: StatusBarNotification) {
        val isNotificationDebugMode: Boolean = BuildConfig.NOTIFICATION_DEBUG_MODE
        val diagnostic: JSONObject = buildNotificationDiagnostic(sbn, includeSensitiveText = isNotificationDebugMode)
        Log.d(TAG, "Notification diagnostic: $diagnostic")
        if (isNotificationDebugMode) {
            saveNotificationDiagnostic(buildNotificationDiagnostic(sbn, includeSensitiveText = false))
        }
    }

    private fun buildNotificationDebugInfo(sbn: StatusBarNotification): String? {
        if (!BuildConfig.NOTIFICATION_DEBUG_MODE) return null
        return buildNotificationDiagnostic(sbn, includeSensitiveText = true).toString(DEBUG_JSON_INDENT_SPACES)
    }

    private fun buildNotificationDiagnostic(sbn: StatusBarNotification, includeSensitiveText: Boolean): JSONObject {
        val notification: AndroidNotification = sbn.notification
        val publicVersion: AndroidNotification? = notification.publicVersion
        return JSONObject().apply {
            put("packageName", sbn.packageName)
            put("postTime", sbn.postTime)
            put("category", notification.category.orEmpty())
            put("channelId", notification.channelId.orEmpty())
            put("visibility", visibilityToString(notification.visibility))
            put("flags", notification.flags)
            @Suppress("DEPRECATION")
            put("priority", notification.priority)
            put("extrasKeySet", JSONArray(notification.extras.safeKeySet().sorted()))
            put("title", describeTextForDiagnostics(notification.extras.getCharSequence(AndroidNotification.EXTRA_TITLE), includeSensitiveText))
            put("text", describeTextForDiagnostics(notification.extras.getCharSequence(AndroidNotification.EXTRA_TEXT), includeSensitiveText))
            put("bigText", describeTextForDiagnostics(notification.extras.getCharSequence(AndroidNotification.EXTRA_BIG_TEXT), includeSensitiveText))
            put("subText", describeTextForDiagnostics(notification.extras.getCharSequence(AndroidNotification.EXTRA_SUB_TEXT), includeSensitiveText))
            put("summaryText", describeTextForDiagnostics(notification.extras.getCharSequence(AndroidNotification.EXTRA_SUMMARY_TEXT), includeSensitiveText))
            put("infoText", describeTextForDiagnostics(notification.extras.getCharSequence(AndroidNotification.EXTRA_INFO_TEXT), includeSensitiveText))
            put("messages", describeValueForDiagnostics(notification.extras.getValueSafely(AndroidNotification.EXTRA_MESSAGES), includeSensitiveText))
            put("parsedMessages", describeParsedMessagesForDiagnostics(notification.extras, includeSensitiveText))
            put("extras", describeBundleForDiagnostics(notification.extras, includeSensitiveText))
            put("publicVersion", publicVersion != null)
            if (publicVersion != null) {
                put("publicVersionDetails", describePublicVersionForDiagnostics(publicVersion, includeSensitiveText))
            }
            put("isClearable", sbn.isClearable)
            put("isOngoing", sbn.isOngoing)
            put("androidSdkVersion", Build.VERSION.SDK_INT)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("hiddenSignals", describeHiddenSignals(notification))
        }
    }

    private fun describePublicVersionForDiagnostics(
        publicVersion: AndroidNotification,
        includeSensitiveText: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("category", publicVersion.category.orEmpty())
            put("channelId", publicVersion.channelId.orEmpty())
            put("visibility", visibilityToString(publicVersion.visibility))
            put("flags", publicVersion.flags)
            @Suppress("DEPRECATION")
            put("priority", publicVersion.priority)
            put("extrasKeySet", JSONArray(publicVersion.extras.safeKeySet().sorted()))
            put("title", describeTextForDiagnostics(publicVersion.extras.getCharSequence(AndroidNotification.EXTRA_TITLE), includeSensitiveText))
            put("text", describeTextForDiagnostics(publicVersion.extras.getCharSequence(AndroidNotification.EXTRA_TEXT), includeSensitiveText))
            put("bigText", describeTextForDiagnostics(publicVersion.extras.getCharSequence(AndroidNotification.EXTRA_BIG_TEXT), includeSensitiveText))
            put("subText", describeTextForDiagnostics(publicVersion.extras.getCharSequence(AndroidNotification.EXTRA_SUB_TEXT), includeSensitiveText))
            put("summaryText", describeTextForDiagnostics(publicVersion.extras.getCharSequence(AndroidNotification.EXTRA_SUMMARY_TEXT), includeSensitiveText))
            put("infoText", describeTextForDiagnostics(publicVersion.extras.getCharSequence(AndroidNotification.EXTRA_INFO_TEXT), includeSensitiveText))
            put("messages", describeValueForDiagnostics(publicVersion.extras.getValueSafely(AndroidNotification.EXTRA_MESSAGES), includeSensitiveText))
            put("parsedMessages", describeParsedMessagesForDiagnostics(publicVersion.extras, includeSensitiveText))
            put("extras", describeBundleForDiagnostics(publicVersion.extras, includeSensitiveText))
        }
    }

    private fun describeHiddenSignals(notification: AndroidNotification): JSONObject {
        val primaryContent: NotificationTextSnapshot = extractNotificationTextSnapshot(notification.extras)
        val publicContent: NotificationTextSnapshot? = notification.publicVersion?.extras?.let { extras ->
            extractNotificationTextSnapshot(extras)
        }
        val classification: HiddenContentClassification = classifyHiddenContent(
            notification = notification,
            primaryContent = primaryContent,
            publicContent = publicContent
        )
        return JSONObject().apply {
            put("isVisibilityPrivate", notification.visibility == AndroidNotification.VISIBILITY_PRIVATE)
            put("isVisibilitySecret", notification.visibility == AndroidNotification.VISIBILITY_SECRET)
            put("hasPublicVersion", notification.publicVersion != null)
            put("primaryHasAnyText", primaryContent.hasAnyText())
            put("primaryHasRealNotificationText", primaryContent.hasRealNotificationText())
            put("primaryHasOnlyHiddenPlaceholderBody", primaryContent.hasOnlyHiddenPlaceholderBody())
            put("publicHasAnyText", publicContent?.hasAnyText() == true)
            put("publicHasOnlyHiddenPlaceholderBody", publicContent?.hasOnlyHiddenPlaceholderBody() == true)
            put("contentStatus", classification.contentStatus.wireName)
            put("reason", classification.reason?.wireName.orEmpty())
        }
    }

    private fun describeBundleForDiagnostics(bundle: Bundle, includeSensitiveText: Boolean): JSONArray {
        val keys: List<String> = bundle.safeKeySet().sorted()
        return JSONArray().apply {
            keys.forEach { key ->
                put(
                    JSONObject().apply {
                        put("key", key)
                        put("value", describeValueForDiagnostics(bundle.getValueSafely(key), includeSensitiveText))
                    }
                )
            }
        }
    }

    private fun describeParsedMessagesForDiagnostics(extras: Bundle, includeSensitiveText: Boolean): JSONArray {
        val messages: List<ParsedMessage> = extractMessagingStyleMessages(extras)
        return JSONArray().apply {
            messages.forEach { message ->
                put(
                    JSONObject().apply {
                        put("sender", describeTextForDiagnostics(message.sender, includeSensitiveText))
                        put("text", describeTextForDiagnostics(message.text, includeSensitiveText))
                    }
                )
            }
        }
    }

    private fun describeTextForDiagnostics(value: CharSequence?, includeSensitiveText: Boolean): JSONObject {
        val text: String = value.toCleanString()
        return JSONObject().apply {
            put("type", value?.javaClass?.name.orEmpty())
            put("hasText", text.isNotBlank())
            put("length", text.length)
            put("isHiddenPlaceholder", isHiddenPlaceholder(text))
            if (includeSensitiveText) {
                put("value", text)
            }
        }
    }

    private fun describeValueForDiagnostics(value: Any?, includeSensitiveText: Boolean): JSONObject {
        return JSONObject().apply {
            put("type", value?.javaClass?.name.orEmpty())
            when (value) {
                null -> {
                    put("hasText", false)
                    put("length", 0)
                }
                is CharSequence -> {
                    val text: String = value.toCleanString()
                    put("hasText", text.isNotBlank())
                    put("length", text.length)
                    put("isHiddenPlaceholder", isHiddenPlaceholder(text))
                    if (includeSensitiveText) {
                        put("value", text)
                    }
                }
                is Array<*> -> {
                    put("size", value.size)
                    put("itemTypes", JSONArray(value.map { item -> item?.javaClass?.name.orEmpty() }))
                }
                is Iterable<*> -> {
                    val items: List<*> = value.toList()
                    put("size", items.size)
                    put("itemTypes", JSONArray(items.map { item -> item?.javaClass?.name.orEmpty() }))
                }
                is Bundle -> {
                    put("keys", JSONArray(value.safeKeySet().sorted()))
                }
                else -> {
                    put("hasText", false)
                    put("stringLength", value.toString().length)
                }
            }
        }
    }

    private fun saveNotificationDiagnostic(diagnostic: JSONObject) {
        try {
            val file = File(filesDir, DEBUG_NOTIFICATION_DIAGNOSTICS_FILE)
            file.appendText(diagnostic.toString() + "\n")
        } catch (e: Exception) {
            Log.d(TAG, "Unable to save notification diagnostic: ${e.message}", e)
        }
    }

    private fun visibilityToString(visibility: Int): String {
        return when (visibility) {
            AndroidNotification.VISIBILITY_PUBLIC -> "public"
            AndroidNotification.VISIBILITY_PRIVATE -> "private"
            AndroidNotification.VISIBILITY_SECRET -> "secret"
            else -> visibility.toString()
        }
    }

    private fun Bundle.safeKeySet(): Set<String> {
        return try {
            keySet()
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read notification extras keys: ${e.message}", e)
            emptySet()
        }
    }

    private fun Bundle.getValueSafely(key: String): Any? {
        return try {
            @Suppress("DEPRECATION")
            get(key)
        } catch (e: Exception) {
            Log.d(TAG, "Unable to read notification extra value: key=$key, error=${e.message}", e)
            null
        }
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

    private fun NotificationTextSnapshot.hasAnyText(): Boolean {
        return allTextValues().any { text -> text.isNotBlank() }
    }

    private fun NotificationTextSnapshot.hasRealNotificationText(): Boolean {
        val hasRealBodyText: Boolean = bodyTextValues().any { text ->
            text.isNotBlank() && !isHiddenPlaceholder(text)
        }
        val hasTitleOnlyText: Boolean = bodyTextValues().none { text -> text.isNotBlank() } &&
            title.isNotBlank() &&
            !isHiddenPlaceholder(title)
        return hasRealBodyText || hasTitleOnlyText
    }

    private fun NotificationTextSnapshot.hasOnlyHiddenPlaceholderBody(): Boolean {
        val bodyValues: List<String> = bodyTextValues().filter { text -> text.isNotBlank() }
        return bodyValues.isNotEmpty() && bodyValues.all { text -> isHiddenPlaceholder(text) }
    }

    private fun NotificationTextSnapshot.allTextValues(): List<String> {
        return listOf(title) + bodyTextValues()
    }

    private fun NotificationTextSnapshot.bodyTextValues(): List<String> {
        return listOf(text, bigText, subText, summaryText, infoText) +
            lines +
            messages.flatMap { message -> listOf(message.sender, message.text) }
    }

    private fun isHiddenPlaceholder(text: String): Boolean {
        val normalizedText: String = text.lowercase()
        return HIDDEN_TEXT_MARKERS.any { marker -> normalizedText.contains(marker) }
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
        private const val DEBUG_JSON_INDENT_SPACES: Int = 2
        private const val NON_BREAKING_SPACE: String = "\u00A0"
        private const val DEBUG_NOTIFICATION_DIAGNOSTICS_FILE: String = "notification_diagnostics.jsonl"
        private const val HIDDEN_NOTIFICATION_FALLBACK_MESSAGE: String =
            "Содержимое уведомления скрыто Android или приложением банка. Оригинальный текст не был передан системе уведомлений."
        private val HIDDEN_TEXT_MARKERS: List<String> = listOf(
            "конфиденциальная информация",
            "уведомление скрыто",
            "content hidden",
            "hidden notification",
            "sensitive content"
        )
    }

    private data class ParsedPushNotification(
        val combinedMessage: String,
        val fingerprint: String,
        val contentStatus: NotificationContentStatus,
        val hiddenReason: NotificationContentHiddenReason?,
    )

    private data class ParsedMessage(
        val sender: String,
        val text: String,
    )

    private data class NotificationTextSnapshot(
        val title: String,
        val text: String,
        val bigText: String,
        val subText: String,
        val summaryText: String,
        val infoText: String,
        val lines: List<String>,
        val messages: List<ParsedMessage>,
    )

    private data class HiddenContentClassification(
        val contentStatus: NotificationContentStatus,
        val reason: NotificationContentHiddenReason?,
    ) {
        companion object {
            fun visible(): HiddenContentClassification {
                return HiddenContentClassification(
                    contentStatus = NotificationContentStatus.VISIBLE,
                    reason = null
                )
            }
        }
    }
}
