package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.utils.NotificationFreshness
import java.io.IOException
import javax.inject.Inject

class SendNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    suspend operator fun invoke(notification: Notification): Result<Unit> {
        if (notificationRepository.wasSentSuccessfully(notification.idempotencyKey)) {
            return Result.success(Unit)
        }
        if (NotificationFreshness.isExpired(notification.timestamp)) {
            return Result.success(Unit)
        }
        notificationRepository.saveToHistory(notification)
        return try {
            val sendResult: Result<Unit> = notificationRepository.sendToServer(notification)
            if (sendResult.isSuccess) {
                notificationRepository.markSentSuccess(notification)
                Result.success(Unit)
            } else {
                enqueueForRetryIfFresh(notification)
                sendResult
            }
        } catch (e: IOException) {
            enqueueForRetryIfFresh(notification)
            Result.failure(e)
        }
    }

    private suspend fun enqueueForRetryIfFresh(notification: Notification) {
        if (NotificationFreshness.isExpired(notification.timestamp)) return
        notificationRepository.saveForRetry(notification)
    }
}
