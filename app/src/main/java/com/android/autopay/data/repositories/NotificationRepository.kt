package com.android.autopay.data.repositories

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.local.db.NotificationHistoryDao
import com.android.autopay.data.local.db.UnsentNotificationDao
import com.android.autopay.data.local.mappers.toHistoryNotificationDBO
import com.android.autopay.data.local.mappers.toNotification
import com.android.autopay.data.local.mappers.toUnsentNotificationDBO
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType
import com.android.autopay.data.utils.CONNECT_URL
import com.android.autopay.data.utils.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStoreManager: DataStoreManager,
    private val unsentNotificationDao: UnsentNotificationDao,
    private val notificationHistoryDao: NotificationHistoryDao,
    private val httpClient: OkHttpClient,
    private val appDispatchers: AppDispatchers
) {

    suspend fun connect(token: String): Result<Unit> {
        return withContext(appDispatchers.io) {
            val headers = Headers.Builder()
                .add("Accept", "application/json")
                .add("Access-Token", token)
                .add("Content-Type", "application/json")
                .build()

            val androidId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val json = JSONObject()
            json.put("android_id", androidId)
            json.put("device_model", Build.MODEL)
            json.put("android_version", Build.VERSION.RELEASE)
            json.put("manufacturer", Build.MANUFACTURER)
            json.put("brand", Build.BRAND)

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val settings = dataStoreManager.getSettings().first()
            val connectUrl: String = buildConnectUrl(settings.url)
            val request = Request.Builder()
                .url(connectUrl)
                .headers(headers)
                .post(requestBody)
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext Result.success(Unit)
                    }

                    val errorBody: String = response.body?.string() ?: ""
                    return@withContext try {
                        val obj = JSONObject(errorBody)
                        val message = obj.optString("message", response.code.toString())
                        Result.failure(Exception(message))
                    } catch (e: Exception) {
                        Result.failure(Exception(response.code.toString()))
                    }
                }
            } catch (e: IOException) {
                return@withContext Result.failure(e)
            }
        }
    }

    suspend fun sendToServer(
        notification: Notification
    ): Result<Unit> {
        val settings = dataStoreManager.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank()) {
            return Result.failure(IllegalStateException("App is not connected"))
        }

        val headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("Idempotency-Key", notification.idempotencyKey)
            .add("Access-Token", settings.token)
            .build()

        val json = JSONObject()
        json.put("sender", notification.sender)
        json.put("message", notification.message)
        json.put("timestamp", notification.timestamp)
        json.put("type", notification.type.wireName)

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(settings.url)
            .headers(headers)
            .post(requestBody)
            .build()

        return withContext(appDispatchers.io) {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success(Unit)
                }

                return@withContext Result.failure(Exception(response.code.toString()))
            }
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

    private fun buildConnectUrl(settingsUrl: String): String {
        val apiSmsSegment: String = "/api/sms"
        val connectPath: String = "/api/app/device/connect"
        return if (settingsUrl.contains(apiSmsSegment)) {
            settingsUrl.replace(apiSmsSegment, connectPath)
        } else {
            val base: String = if (settingsUrl.endsWith("/")) settingsUrl.dropLast(1) else settingsUrl
            "$base$connectPath"
        }
    }
}