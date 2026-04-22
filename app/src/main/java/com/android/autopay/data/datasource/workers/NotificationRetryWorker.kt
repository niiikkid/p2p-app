package com.android.autopay.data.datasource.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.autopay.domain.usecases.RetryPendingNotificationsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NotificationRetryWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val retryPendingNotificationsUseCase: RetryPendingNotificationsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            when (retryPendingNotificationsUseCase()) {
                is RetryPendingNotificationsUseCase.Result.AllSent -> Result.success()
                is RetryPendingNotificationsUseCase.Result.NothingToDo -> Result.success()
                is RetryPendingNotificationsUseCase.Result.SomeFailed -> Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.failure()
        }
    }

    companion object {
        const val TAG: String = "NotificationRetryWorker"
    }
}
