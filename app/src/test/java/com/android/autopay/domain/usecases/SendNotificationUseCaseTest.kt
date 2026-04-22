package com.android.autopay.domain.usecases

import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationType
import com.android.autopay.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SendNotificationUseCaseTest {

    @Test
    fun `invoke persists history and marks sent on success`() = runBlocking {
        val inputNotification: Notification = buildNotification()
        val mockRepository = FakeNotificationRepository(sendResult = Result.success(Unit))
        val useCase = SendNotificationUseCase(notificationRepository = mockRepository)
        val actualResult: Result<Unit> = useCase(inputNotification)
        assertTrue("Expected success", actualResult.isSuccess)
        assertEquals(listOf(inputNotification), mockRepository.savedToHistory)
        assertEquals(listOf(inputNotification), mockRepository.sentToServer)
        assertEquals(listOf(inputNotification), mockRepository.markedSentSuccess)
        assertTrue(mockRepository.savedForRetry.isEmpty())
    }

    @Test
    fun `invoke enqueues for retry when server returns failure`() = runBlocking {
        val inputNotification: Notification = buildNotification()
        val expectedError = IllegalStateException("server error")
        val mockRepository = FakeNotificationRepository(sendResult = Result.failure(expectedError))
        val useCase = SendNotificationUseCase(notificationRepository = mockRepository)
        val actualResult: Result<Unit> = useCase(inputNotification)
        assertTrue("Expected failure", actualResult.isFailure)
        assertEquals(expectedError, actualResult.exceptionOrNull())
        assertEquals(listOf(inputNotification), mockRepository.savedToHistory)
        assertEquals(listOf(inputNotification), mockRepository.sentToServer)
        assertTrue(mockRepository.markedSentSuccess.isEmpty())
        assertEquals(listOf(inputNotification), mockRepository.savedForRetry)
    }

    @Test
    fun `invoke enqueues for retry when IO exception is thrown`() = runBlocking {
        val inputNotification: Notification = buildNotification()
        val expectedError = IOException("offline")
        val mockRepository = FakeNotificationRepository(sendError = expectedError)
        val useCase = SendNotificationUseCase(notificationRepository = mockRepository)
        val actualResult: Result<Unit> = useCase(inputNotification)
        assertTrue("Expected failure", actualResult.isFailure)
        assertEquals(expectedError, actualResult.exceptionOrNull())
        assertEquals(listOf(inputNotification), mockRepository.savedToHistory)
        assertTrue(mockRepository.markedSentSuccess.isEmpty())
        assertEquals(listOf(inputNotification), mockRepository.savedForRetry)
    }

    private fun buildNotification(): Notification {
        return Notification(
            sender = "sender",
            message = "message",
            timestamp = 1_000L,
            type = NotificationType.SMS,
            idempotencyKey = "key"
        )
    }

    private class FakeNotificationRepository(
        private val sendResult: Result<Unit> = Result.success(Unit),
        private val sendError: IOException? = null,
    ) : NotificationRepository {

        val savedToHistory: MutableList<Notification> = mutableListOf()
        val sentToServer: MutableList<Notification> = mutableListOf()
        val markedSentSuccess: MutableList<Notification> = mutableListOf()
        val savedForRetry: MutableList<Notification> = mutableListOf()

        override suspend fun connect(token: String): Result<Unit> = Result.success(Unit)
        override suspend fun pingServer(token: String): Result<Unit> = Result.success(Unit)

        override suspend fun sendToServer(notification: Notification): Result<Unit> {
            sentToServer.add(notification)
            sendError?.let { throw it }
            return sendResult
        }

        override suspend fun saveToHistory(notification: Notification) {
            savedToHistory.add(notification)
        }

        override suspend fun markSentSuccess(notification: Notification, sentAtTimestamp: Long) {
            markedSentSuccess.add(notification)
        }

        override fun observeHistory(): Flow<List<Notification>> = flowOf(emptyList())
        override fun observeLatest(limit: Int): Flow<List<Notification>> = flowOf(emptyList())
        override fun observeCount(): Flow<Int> = flowOf(0)
        override fun observeSmsSenders(): Flow<List<String>> = flowOf(emptyList())
        override fun observeBankApplications(): Flow<List<String>> = flowOf(emptyList())
        override fun observeRetryQueueCount(): Flow<Int> = flowOf(0)
        override suspend fun getDeviceSmsSenders(): List<String> = emptyList()
        override suspend fun getInstalledApplicationPackages(): List<String> = emptyList()
        override suspend fun getHistoryCount(query: String?): Int = 0
        override suspend fun getHistoryPage(query: String?, limit: Int, offset: Int): List<Notification> = emptyList()

        override suspend fun saveForRetry(notification: Notification) {
            savedForRetry.add(notification)
        }

        override suspend fun getForRetry(): List<Notification> = emptyList()
        override suspend fun deleteForRetry(notification: Notification) {}
    }
}
