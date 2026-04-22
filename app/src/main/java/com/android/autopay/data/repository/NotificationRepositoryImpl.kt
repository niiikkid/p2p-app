package com.android.autopay.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import com.android.autopay.data.datasource.local.db.NotificationHistoryDao
import com.android.autopay.data.datasource.local.db.UnsentNotificationDao
import com.android.autopay.data.models.mappers.toHistoryNotificationDBO
import com.android.autopay.data.models.mappers.toNotification
import com.android.autopay.data.models.mappers.toUnsentNotificationDBO
import com.android.autopay.data.datasource.remote.NotificationApi
import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationType
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.domain.repository.SettingsRepository
import com.android.autopay.utils.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val unsentNotificationDao: UnsentNotificationDao,
    private val notificationHistoryDao: NotificationHistoryDao,
    private val notificationApi: NotificationApi,
    private val appDispatchers: AppDispatchers
) : NotificationRepository {

    override suspend fun connect(token: String): Result<Unit> {
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

    override suspend fun pingServer(token: String): Result<Unit> {
        return notificationApi.ping(token)
    }

    override suspend fun sendToServer(notification: Notification): Result<Unit> {
        val settings = settingsRepository.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank()) {
            return Result.failure(IllegalStateException("App is not connected"))
        }
        return notificationApi.sendNotification(settings.token, notification)
    }

    override suspend fun saveToHistory(notification: Notification) {
        if (notificationHistoryDao.existsByIdempotencyKey(notification.idempotencyKey)) return
        notificationHistoryDao.upsert(notification.toHistoryNotificationDBO())
    }

    override suspend fun markSentSuccess(notification: Notification, sentAtTimestamp: Long) {
        notificationHistoryDao.updateSentAtByIdempotencyKey(notification.idempotencyKey, sentAtTimestamp)
    }

    override fun observeHistory(): Flow<List<Notification>> {
        return notificationHistoryDao.observeAll().map { list -> list.map { it.toNotification() } }
    }

    override fun observeLatest(limit: Int): Flow<List<Notification>> {
        return notificationHistoryDao.observeLatest(limit).map { list -> list.map { it.toNotification() } }
    }

    override fun observeCount(): Flow<Int> {
        return notificationHistoryDao.observeCount()
    }

    override fun observeSmsSenders(): Flow<List<String>> {
        return notificationHistoryDao.observeDistinctSendersByType(NotificationType.SMS.wireName)
    }

    override fun observeBankApplications(): Flow<List<String>> {
        return notificationHistoryDao.observeDistinctSendersByType(NotificationType.PUSH.wireName)
    }

    override fun observeRetryQueueCount(): Flow<Int> {
        return unsentNotificationDao.observeCount()
    }

    override suspend fun getDeviceSmsSenders(): List<String> = withContext(appDispatchers.io) {
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

    override suspend fun getInstalledApplicationPackages(): List<String> = withContext(appDispatchers.io) {
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

    override suspend fun getHistoryPage(query: String?, limit: Int, offset: Int): List<Notification> {
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

    override suspend fun saveForRetry(notification: Notification) {
        if (unsentNotificationDao.existsByIdempotencyKey(notification.idempotencyKey)) return
        unsentNotificationDao.upsert(notification.toUnsentNotificationDBO())
    }

    override suspend fun getForRetry(): List<Notification> {
        return unsentNotificationDao.getAll().map { it.toNotification() }
    }

    override suspend fun deleteForRetry(notification: Notification) {
        unsentNotificationDao.delete(notification.toUnsentNotificationDBO())
    }
}
