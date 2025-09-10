package com.android.autopay.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.autopay.data.utils.AppDispatchers
import com.android.autopay.data.utils.PING_ENDPOINT_PATH
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request

@HiltWorker
class PingWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val httpClient: OkHttpClient,
    private val dataStoreManager: com.android.autopay.data.DataStoreManager,
    private val appDispatchers: AppDispatchers
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = dataStoreManager.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank()) {
            return Result.success()
        }

        val baseUrl: String = settings.url
        val pingUrl: String = buildPingUrl(baseUrl)

        val headers = Headers.Builder()
            .add("Accept", "application/json")
            .add("Access-Token", settings.token)
            .build()

        val request = Request.Builder()
            .url(pingUrl)
            .headers(headers)
            .get()
            .build()

        return withContext(appDispatchers.io) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success()
                    } else {
                        Result.success()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ping failed: ${e.message}", e)
                Result.success()
            }
        }
    }

    private fun buildPingUrl(settingsUrl: String): String {
        val apiSmsSegment: String = "/api/app/sms"
        return if (settingsUrl.contains(apiSmsSegment)) {
            settingsUrl.replace(apiSmsSegment, PING_ENDPOINT_PATH)
        } else {
            val base: String = if (settingsUrl.endsWith("/")) settingsUrl.dropLast(1) else settingsUrl
            "$base$PING_ENDPOINT_PATH"
        }
    }

    companion object {
        private const val TAG: String = "PingWorker"
    }
}


