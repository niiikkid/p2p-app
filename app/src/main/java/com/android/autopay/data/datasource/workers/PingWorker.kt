package com.android.autopay.data.datasource.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.android.autopay.domain.usecases.PingServerUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PingWorker
@AssistedInject
constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pingServerUseCase: PingServerUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return pingServerUseCase().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                Log.d(TAG, "Ping failed: ${error.message}", error)
                Result.success()
            }
        )
    }

    companion object {
        private const val TAG: String = "PingWorker"
    }
}
