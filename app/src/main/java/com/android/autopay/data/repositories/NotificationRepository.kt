package com.android.autopay.data.repositories

import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.local.db.NotificationHistoryDao
import com.android.autopay.data.local.db.UnsentNotificationDao
import com.android.autopay.data.local.mappers.toHistoryNotificationDBO
import com.android.autopay.data.local.mappers.toNotification
import com.android.autopay.data.local.mappers.toUnsentNotificationDBO
import com.android.autopay.data.models.Notification
import com.android.autopay.data.utils.DEFAULT_URL
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

class NotificationRepository @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val unsentNotificationDao: UnsentNotificationDao,
    private val notificationHistoryDao: NotificationHistoryDao
) {

    suspend fun sendToServer(
        notification: Notification
    ): Result<Unit> {
        val client = OkHttpClient()

        val settings = dataStoreManager.getSettings().first()

        val headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("Idempotency-Key", notification.idempotencyKey)
            .add("Access-Token", settings.token)
            .build()

        val json = JSONObject()
        json.put("sender", notification.sender)
        json.put("message", notification.message)
        json.put("timestamp", notification.timestamp)
        json.put("type", notification.type)

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(DEFAULT_URL)
            .headers(headers)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return Result.success(Unit)
            }

            return Result.failure(Exception(response.code.toString()))
        }
    }

    suspend fun saveToHistory(notification: Notification) {
        notificationHistoryDao.upsert(notification.toHistoryNotificationDBO())
    }

    fun observeHistory(): Flow<List<Notification>> {
        return notificationHistoryDao.observeAll().map { list ->
            list.map { it.toNotification() }
        }
    }

    suspend fun saveForRetry(notification: Notification) {
        unsentNotificationDao.upsert(notification.toUnsentNotificationDBO())
    }

    suspend fun getForRetry(): List<Notification> {
        return unsentNotificationDao.getAll().map { it.toNotification() }
    }

    suspend fun deleteForRetry(notification: Notification) {
        unsentNotificationDao.delete(notification.toUnsentNotificationDBO())
    }
}