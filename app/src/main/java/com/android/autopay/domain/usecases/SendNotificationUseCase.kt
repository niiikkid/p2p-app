package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.utils.NotificationFreshness
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class SendNotificationUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    suspend operator fun invoke(notification: Notification): Result<Unit> {
        return withIdempotencyLock(notification.idempotencyKey) {
            if (notificationRepository.wasSentSuccessfully(notification.idempotencyKey)) {
                notificationRepository.deleteForRetry(notification)
                return@withIdempotencyLock Result.success(Unit)
            }
            if (NotificationFreshness.isExpired(notification.timestamp)) {
                notificationRepository.deleteForRetry(notification)
                return@withIdempotencyLock Result.success(Unit)
            }
            notificationRepository.saveToHistory(notification)
            return@withIdempotencyLock try {
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
    }

    private suspend fun enqueueForRetryIfFresh(notification: Notification) {
        if (NotificationFreshness.isExpired(notification.timestamp)) return
        notificationRepository.saveForRetry(notification)
    }

    private suspend fun <T> withIdempotencyLock(idempotencyKey: String, block: suspend () -> T): T {
        val mutex: Mutex = idempotencyLocks.computeIfAbsent(idempotencyKey) { Mutex() }
        return mutex.withLock { block() }
    }

    private companion object {
        val idempotencyLocks: ConcurrentHashMap<String, Mutex> = ConcurrentHashMap()
    }
}
