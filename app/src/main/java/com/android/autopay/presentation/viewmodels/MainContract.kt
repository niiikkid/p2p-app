package com.android.autopay.presentation.viewmodels

import com.android.autopay.domain.models.Notification

object MainContract {

    const val DEFAULT_PAGE_SIZE: Int = 20
    const val LATEST_NOTIFICATIONS_LIMIT: Int = 5

    data class State(
        val token: String = "",
        val notificationStats: NotificationStats = NotificationStats(),
        val isSavePossible: Boolean = false,
        val isConnected: Boolean = false,
        val isConnecting: Boolean = false,
        val errorMessage: String? = null,
        val deviceInfo: DeviceInfo = DeviceInfo(),
        val searchQuery: String = "",
        val pagedNotifications: List<Notification> = emptyList(),
        val isPageLoading: Boolean = false,
        val nextOffset: Int = 0,
        val pageSize: Int = DEFAULT_PAGE_SIZE,
        val canLoadMore: Boolean = true,
        val isAutomationEnabled: Boolean = true,
        val lastSuccessfulPingAt: Long = 0L,
        val lastPingElapsedSeconds: Long = 0L,
        val selectedSmsSenders: Set<String> = emptySet(),
        val selectedBankApplications: Set<String> = emptySet(),
        val knownSmsSenders: Set<String> = emptySet(),
        val knownBankApplications: Set<String> = emptySet(),
        val availableSmsSenders: List<String> = emptyList(),
        val availableBankApplications: List<String> = emptyList()
    ) {

        data class NotificationStats(
            val receiveAll: Int = 0,
            val lastNotifications: List<Notification> = emptyList()
        )

        data class DeviceInfo(
            val deviceName: String = "",
            val manufacturer: String = "",
            val model: String = "",
            val androidVersion: String = "",
            val apiLevel: String = "",
            val buildNumber: String = "",
            val cpuAbi: String = ""
        )
    }

    sealed class Intent {
        data class ChangeToken(val token: String) : Intent()
        data object Save : Intent()
        data class ReRegister(val token: String) : Intent()
        data class ChangeSearchQuery(val query: String) : Intent()
        data object LoadMoreLogs : Intent()
        data object ToggleAutomation : Intent()
        data class ToggleSmsSource(val sender: String) : Intent()
        data class ToggleBankApplicationSource(val packageName: String) : Intent()
        data object ClearSmsSources : Intent()
        data object ClearBankApplicationSources : Intent()
        data object RefreshSourceFilters : Intent()
    }
}
