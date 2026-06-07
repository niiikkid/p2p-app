package com.android.autopay.domain.repository

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.PingResult
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    suspend fun connect(token: String): Result<Unit>
    suspend fun pingServer(token: String): Result<PingResult>
    suspend fun sendToServer(notification: Notification): Result<Unit>
    suspend fun saveToHistory(notification: Notification)
    suspend fun wasSentSuccessfully(idempotencyKey: String): Boolean
    suspend fun markSentSuccess(notification: Notification, sentAtTimestamp: Long = System.currentTimeMillis())
    fun observeHistory(): Flow<List<Notification>>
    fun observeLatest(limit: Int): Flow<List<Notification>>
    fun observeCount(): Flow<Int>
    fun observeSmsSenders(): Flow<List<String>>
    fun observeBankApplications(): Flow<List<String>>
    fun observeRetryQueueCount(): Flow<Int>
    suspend fun getDeviceSmsSenders(): List<String>
    suspend fun getInstalledApplicationPackages(): List<String>
    suspend fun getHistoryCount(query: String?): Int
    suspend fun getHistoryPage(query: String?, limit: Int, offset: Int): List<Notification>
    suspend fun saveForRetry(notification: Notification)
    suspend fun getForRetry(): List<Notification>
    suspend fun deleteForRetry(notification: Notification)
}
