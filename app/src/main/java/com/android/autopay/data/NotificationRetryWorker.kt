package com.android.autopay.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.repositories.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.io.IOException


@HiltWorker
class NotificationRetryWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dataStoreManager: DataStoreManager,
    private val repository: NotificationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val settings = dataStoreManager.getSettings().first()
            if (!settings.isAutomationEnabled || !settings.isConnected || settings.token.isBlank()) {
                return@coroutineScope Result.success()
            }
            val notifications = repository.getForRetry()
            Log.d(TAG, "Found ${notifications.size} notifications to retry")
            val deferredResults = notifications.map { notification ->
                async {
                    try {
                        val result = repository.sendToServer(notification)
                        if (result.isSuccess) {
                            repository.deleteForRetry(notification)
                            repository.markSentSuccess(notification)
                            true
                        } else {
                            false
                        }
                    } catch (e: IOException) {
                        Log.w(
                            TAG,
                            "Не удалось отправить уведомление. Оставляем в очереди на повтор.",
                            e
                        )
                        false
                    }
                }
            }

            val results = deferredResults.awaitAll()

            if (results.all { it }) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "NotificationRetryWorker"
    }
}