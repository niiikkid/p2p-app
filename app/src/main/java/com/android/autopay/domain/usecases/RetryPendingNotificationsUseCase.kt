package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationType
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.domain.repository.SettingsRepository
import com.android.autopay.utils.NotificationFreshness
import com.android.autopay.utils.SourceFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RetryPendingNotificationsUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
    private val sendNotificationUseCase: SendNotificationUseCase,
) {

    suspend operator fun invoke(): Result = coroutineScope {
        val settings = settingsRepository.getSettings().first()
        if (!settings.isAutomationEnabled || !settings.isConnected || settings.token.isBlank()) {
            return@coroutineScope Result.NothingToDo
        }
        val pending: List<Notification> = notificationRepository.getForRetry()
        if (pending.isEmpty()) return@coroutineScope Result.NothingToDo
        val deferred = pending.map { notification ->
            async { processOne(notification, settings.selectedSmsSenders, settings.selectedBankApplications) }
        }
        val allOk: Boolean = deferred.map { it.await() }.all { it }
        if (allOk) Result.AllSent else Result.SomeFailed
    }

    private suspend fun processOne(
        notification: Notification,
        selectedSmsSenders: Set<String>,
        selectedBankApplications: Set<String>,
    ): Boolean {
        if (NotificationFreshness.isExpired(notification.timestamp)) {
            notificationRepository.deleteForRetry(notification)
            return true
        }
        if (notificationRepository.wasSentSuccessfully(notification.idempotencyKey)) {
            notificationRepository.deleteForRetry(notification)
            return true
        }
        val isSmsFilteredOut: Boolean = notification.type == NotificationType.SMS &&
            !SourceFilter.isSmsAllowed(notification.sender, selectedSmsSenders)
        val isPushFilteredOut: Boolean = notification.type == NotificationType.PUSH &&
            !SourceFilter.isBankApplicationAllowed(notification.sender, selectedBankApplications)
        if (isSmsFilteredOut || isPushFilteredOut) {
            notificationRepository.deleteForRetry(notification)
            return true
        }
        val sendResult: kotlin.Result<Unit> = sendNotificationUseCase(notification)
        return sendResult.isSuccess
    }

    sealed class Result {
        data object AllSent : Result()
        data object SomeFailed : Result()
        data object NothingToDo : Result()
    }
}
