package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNotificationHistoryUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    fun observeCount(): Flow<Int> = notificationRepository.observeCount()

    fun observeLatest(limit: Int): Flow<List<Notification>> = notificationRepository.observeLatest(limit)

    fun observeRetryQueueCount(): Flow<Int> = notificationRepository.observeRetryQueueCount()

    fun observeSmsSenders(): Flow<List<String>> = notificationRepository.observeSmsSenders()

    fun observeBankApplications(): Flow<List<String>> = notificationRepository.observeBankApplications()

    suspend fun getCount(query: String?): Int {
        return notificationRepository.getHistoryCount(query = query)
    }

    suspend fun getPage(query: String?, limit: Int, offset: Int): List<Notification> {
        return notificationRepository.getHistoryPage(query = query, limit = limit, offset = offset)
    }
}
