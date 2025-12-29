package com.android.autopay.data.network

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext
import com.android.autopay.data.models.Notification
import com.android.autopay.data.utils.AppDispatchers

class NotificationApi @Inject constructor(
    private val httpClient: OkHttpClient,
    private val appDispatchers: AppDispatchers
) {

    data class DeviceInfoPayload(
        val androidId: String,
        val deviceModel: String,
        val androidVersion: String,
        val manufacturer: String,
        val brand: String
    )

    suspend fun connect(token: String, payload: DeviceInfoPayload): Result<Unit> {
        val headers: Headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("Access-Token", token)
            .add("Content-Type", "application/json")
            .build()

        val json: JSONObject = JSONObject().apply {
            put("android_id", payload.androidId)
            put("device_model", payload.deviceModel)
            put("android_version", payload.androidVersion)
            put("manufacturer", payload.manufacturer)
            put("brand", payload.brand)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request: Request = Request.Builder()
            .url(ApiEndpoints.getConnectUrl())
            .headers(headers)
            .post(requestBody)
            .build()

        return execute(request) { body, code ->
            parseError(body, code)
        }
    }

    suspend fun sendNotification(token: String, notification: Notification): Result<Unit> {
        val headers: Headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("Idempotency-Key", notification.idempotencyKey)
            .add("Access-Token", token)
            .build()

        val json: JSONObject = JSONObject().apply {
            put("sender", notification.sender)
            put("message", notification.message)
            put("timestamp", notification.timestamp)
            put("type", notification.type.wireName)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request: Request = Request.Builder()
            .url(ApiEndpoints.getSmsUrl())
            .headers(headers)
            .post(requestBody)
            .build()

        return execute(request) { _, code ->
            Exception(code.toString())
        }
    }

    suspend fun ping(token: String): Result<Unit> {
        val headers: Headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("Access-Token", token)
            .build()

        val request: Request = Request.Builder()
            .url(ApiEndpoints.getPingUrl())
            .headers(headers)
            .get()
            .build()

        return execute(request) { _, code ->
            Exception(code.toString())
        }
    }

    private suspend fun execute(
        request: Request,
        errorMapper: (String, Int) -> Exception
    ): Result<Unit> {
        return withContext(appDispatchers.io) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext Result.success(Unit)
                    }
                    val errorBody: String = response.body?.string().orEmpty()
                    Result.failure(errorMapper(errorBody, response.code))
                }
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    private fun parseError(body: String, code: Int): Exception {
        return try {
            val json = JSONObject(body)
            val message: String = json.optString("message", code.toString())
            Exception(message)
        } catch (e: Exception) {
            Exception(code.toString())
        }
    }
}

