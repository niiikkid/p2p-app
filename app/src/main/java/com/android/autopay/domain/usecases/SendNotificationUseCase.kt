package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.repository.NotificationRepository
import java.io.IOException
import javax.inject.Inject

class SendNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    suspend operator fun invoke(notification: Notification): Result<Unit> {
        notificationRepository.saveToHistory(notification)
        return try {
            val sendResult: Result<Unit> = notificationRepository.sendToServer(notification)
            if (sendResult.isSuccess) {
                notificationRepository.markSentSuccess(notification)
                Result.success(Unit)
            } else {
                notificationRepository.saveForRetry(notification)
                sendResult
            }
        } catch (e: IOException) {
            notificationRepository.saveForRetry(notification)
            Result.failure(e)
        }
    }
}
