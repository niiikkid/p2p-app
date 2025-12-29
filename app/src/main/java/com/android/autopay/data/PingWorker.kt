package com.android.autopay.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.autopay.data.network.NotificationApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PingWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dataStoreManager: com.android.autopay.data.DataStoreManager,
    private val notificationApi: NotificationApi
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = dataStoreManager.getSettings().first()
        if (!settings.isConnected || settings.token.isBlank()) {
            return Result.success()
        }

        val pingResult = notificationApi.ping(settings.token)
        return pingResult.fold(
            onSuccess = { Result.success() },
            onFailure = {
                Log.d(TAG, "Ping failed: ${it.message}", it)
                Result.success()
            }
        )
    }

    companion object {
        private const val TAG: String = "PingWorker"
    }
}


