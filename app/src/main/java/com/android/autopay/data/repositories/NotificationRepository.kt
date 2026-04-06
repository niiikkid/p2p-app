package com.android.autopay.data.repositories

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.provider.Settings
import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.local.db.NotificationHistoryDao
import com.android.autopay.data.local.db.UnsentNotificationDao
import com.android.autopay.data.local.mappers.toHistoryNotificationDBO
import com.android.autopay.data.local.mappers.toNotification
import com.android.autopay.data.local.mappers.toUnsentNotificationDBO
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType
import com.android.autopay.data.network.NotificationApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val unsentNotificationDao: UnsentNotificationDao,
    private val notificationHistoryDao: NotificationHistoryDao,
    private val notificationApi: NotificationApi
) {

    suspend fun connect(token: String): Result<Unit> {
        val androidId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val payload: NotificationApi.DeviceInfoPayload = NotificationApi.DeviceInfoPayload(
            androidId = androidId,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND
        )
        return notificationApi.connect(token, payload)
    }

    suspend fun sendToServer(
        notification: Notification
    ): Result<Unit> {
        val settings = dataStoreManager.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank()) {
            return Result.failure(IllegalStateException("App is not connected"))
        }

        return notificationApi.sendNotification(settings.token, notification)
    }

    suspend fun saveToHistory(notification: Notification) {
        if (notificationHistoryDao.existsByIdempotencyKey(notification.idempotencyKey)) return
        notificationHistoryDao.upsert(notification.toHistoryNotificationDBO())
    }

    suspend fun markSentSuccess(notification: Notification, sentAtTimestamp: Long = System.currentTimeMillis()) {
        notificationHistoryDao.updateSentAtByIdempotencyKey(notification.idempotencyKey, sentAtTimestamp)
    }

    fun observeHistory(): Flow<List<Notification>> {
        return notificationHistoryDao.observeAll().map { list ->
            list.map { it.toNotification() }
        }
    }

    fun observeLatest(limit: Int): Flow<List<Notification>> {
        return notificationHistoryDao.observeLatest(limit).map { list ->
            list.map { it.toNotification() }
        }
    }

    fun observeCount(): Flow<Int> {
        return notificationHistoryDao.observeCount()
    }
    fun observeSmsSenders(): Flow<List<String>> {
        return notificationHistoryDao.observeDistinctSendersByType(NotificationType.SMS.wireName)
    }
    fun observeBankApplications(): Flow<List<String>> {
        return notificationHistoryDao.observeDistinctSendersByType(NotificationType.PUSH.wireName)
    }
    suspend fun getDeviceSmsSenders(): List<String> = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            ) ?: return@withContext emptyList()
            cursor.use { safeCursor ->
                val addressIndex: Int = safeCursor.getColumnIndex(Telephony.Sms.ADDRESS)
                if (addressIndex < 0) return@withContext emptyList()
                val senders: MutableSet<String> = mutableSetOf()
                while (safeCursor.moveToNext()) {
                    val sender: String = safeCursor.getString(addressIndex).orEmpty().trim()
                    if (sender.isNotBlank()) {
                        senders.add(sender)
                    }
                }
                return@withContext senders.toList()
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    suspend fun getInstalledApplicationPackages(): List<String> = withContext(Dispatchers.IO) {
        try {
            val packageManager: PackageManager = context.packageManager
            val installedApplications: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
            installedApplications
                .filter { appInfo ->
                    val isSystemApp: Boolean = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val isUpdatedSystemApp: Boolean = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                    val isCurrentApp: Boolean = appInfo.packageName == context.packageName
                    (!isSystemApp || isUpdatedSystemApp) && !isCurrentApp
                }
                .map { it.packageName }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun observeRetryQueueCount(): Flow<Int> {
        return unsentNotificationDao.observeCount()
    }

    suspend fun getHistoryPage(query: String?, limit: Int, offset: Int): List<Notification> {
        val pattern: String? = query?.let { buildLikePattern(it) }
        val items = notificationHistoryDao.getPage(pattern = pattern, limit = limit, offset = offset)
            .map { it.toNotification() }
        return items.map { item ->
            val isQueued = unsentNotificationDao.existsByIdempotencyKey(item.idempotencyKey)
            item.copy(queuedForRetry = isQueued)
        }
    }

    private fun buildLikePattern(input: String): String {
        val escaped: String = input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }

    suspend fun saveForRetry(notification: Notification) {
        if (unsentNotificationDao.existsByIdempotencyKey(notification.idempotencyKey)) return
        unsentNotificationDao.upsert(notification.toUnsentNotificationDBO())
    }

    suspend fun getForRetry(): List<Notification> {
        return unsentNotificationDao.getAll().map { it.toNotification() }
    }

    suspend fun deleteForRetry(notification: Notification) {
        unsentNotificationDao.delete(notification.toUnsentNotificationDBO())
    }
}