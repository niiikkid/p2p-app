package com.android.autopay.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.autopay.data.utils.AppDispatchers
import com.android.autopay.data.utils.PING_ENDPOINT_PATH
import com.android.autopay.data.utils.UrlBuilder
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

        val pingUrl: String = UrlBuilder.buildAbsoluteUrl(PING_ENDPOINT_PATH)

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

    // URL строится через UrlBuilder и BuildConfig.API_HOST

    companion object {
        private const val TAG: String = "PingWorker"
    }
}


