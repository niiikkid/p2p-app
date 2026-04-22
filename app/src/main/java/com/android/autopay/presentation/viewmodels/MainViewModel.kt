package com.android.autopay.presentation.viewmodels

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.autopay.domain.repository.NotificationRepository
import com.android.autopay.domain.repository.SettingsRepository
import com.android.autopay.domain.usecases.ConnectDeviceUseCase
import com.android.autopay.domain.usecases.ObserveNotificationHistoryUseCase
import com.android.autopay.domain.usecases.ToggleAutomationUseCase
import com.android.autopay.utils.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val connectDeviceUseCase: ConnectDeviceUseCase,
    private val observeNotificationHistoryUseCase: ObserveNotificationHistoryUseCase,
    private val toggleAutomationUseCase: ToggleAutomationUseCase,
) : ViewModel() {

    private val _state: MutableStateFlow<MainContract.State> = MutableStateFlow(MainContract.State())
    val state: StateFlow<MainContract.State> = _state.asStateFlow()
    private var historySmsSenders: List<String> = emptyList()
    private var historyBankApplications: List<String> = emptyList()
    private var deviceSmsSenders: List<String> = emptyList()
    private var deviceInstalledApplications: List<String> = emptyList()

    init {
        viewModelScope.launch {
            val settingsData = settingsRepository.getSettings().first()
            _state.value = state.value.copy(
                token = settingsData.token,
                isConnected = settingsData.isConnected,
                isAutomationEnabled = settingsData.isAutomationEnabled,
                lastSuccessfulPingAt = settingsData.lastSuccessfulPingAt,
                selectedSmsSenders = SourceFilter.normalizeSources(settingsData.selectedSmsSenders),
                selectedBankApplications = SourceFilter.normalizeSources(settingsData.selectedBankApplications),
                knownSmsSenders = SourceFilter.normalizeSources(settingsData.knownSmsSenders),
                knownBankApplications = SourceFilter.normalizeSources(settingsData.knownBankApplications)
            )
            updateAvailableSources()
            updateIsSavePossible()
        }
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settingsData ->
                _state.value = state.value.copy(
                    selectedSmsSenders = SourceFilter.normalizeSources(settingsData.selectedSmsSenders),
                    selectedBankApplications = SourceFilter.normalizeSources(settingsData.selectedBankApplications),
                    knownSmsSenders = SourceFilter.normalizeSources(settingsData.knownSmsSenders),
                    knownBankApplications = SourceFilter.normalizeSources(settingsData.knownBankApplications)
                )
                updateAvailableSources()
            }
        }
        viewModelScope.launch { refreshDeviceSourceLists() }
        viewModelScope.launch {
            observeNotificationHistoryUseCase.observeCount().collect { totalCount ->
                _state.value = state.value.copy(
                    notificationStats = state.value.notificationStats.copy(receiveAll = totalCount)
                )
                loadFirstPage()
            }
        }
        viewModelScope.launch {
            observeNotificationHistoryUseCase.observeLatest(MainContract.LATEST_NOTIFICATIONS_LIMIT).collect { latest ->
                _state.value = state.value.copy(
                    notificationStats = state.value.notificationStats.copy(lastNotifications = latest)
                )
                loadFirstPage()
            }
        }
        viewModelScope.launch {
            observeNotificationHistoryUseCase.observeSmsSenders().collect { senders ->
                historySmsSenders = senders
                updateAvailableSources()
            }
        }
        viewModelScope.launch {
            observeNotificationHistoryUseCase.observeBankApplications().collect { applications ->
                historyBankApplications = applications
                updateAvailableSources()
            }
        }
        viewModelScope.launch { loadFirstPage() }
        viewModelScope.launch {
            observeNotificationHistoryUseCase.observeRetryQueueCount()
                .distinctUntilChanged()
                .collect { loadFirstPage() }
        }
        viewModelScope.launch {
            _state.value = state.value.copy(
                deviceInfo = MainContract.State.DeviceInfo(
                    deviceName = Build.MODEL,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                    apiLevel = Build.VERSION.SDK_INT.toString(),
                    buildNumber = Build.DISPLAY,
                    cpuAbi = Build.SUPPORTED_ABIS[0]
                )
            )
        }
        viewModelScope.launch {
            settingsRepository.getSettings()
                .map { it.lastSuccessfulPingAt }
                .distinctUntilChanged()
                .collect { lastPingAt ->
                    _state.value = state.value.copy(lastSuccessfulPingAt = lastPingAt)
                }
        }
        viewModelScope.launch {
            while (true) {
                val elapsedSeconds: Long = if (state.value.lastSuccessfulPingAt == 0L) {
                    0L
                } else {
                    (System.currentTimeMillis() - state.value.lastSuccessfulPingAt) / MILLIS_IN_SECOND
                }
                _state.value = state.value.copy(lastPingElapsedSeconds = elapsedSeconds)
                delay(PING_ELAPSED_UPDATE_DELAY_MS)
            }
        }
    }

    fun onIntent(intent: MainContract.Intent) {
        when (intent) {
            is MainContract.Intent.ChangeToken -> onChangeToken(intent)
            is MainContract.Intent.Save -> onSave()
            is MainContract.Intent.ReRegister -> onReRegister(intent)
            is MainContract.Intent.ChangeSearchQuery -> onChangeSearchQuery(intent)
            is MainContract.Intent.LoadMoreLogs -> onLoadMore()
            is MainContract.Intent.ToggleAutomation -> onToggleAutomation()
            is MainContract.Intent.ToggleSmsSource -> onToggleSmsSource(intent)
            is MainContract.Intent.ToggleBankApplicationSource -> onToggleBankApplicationSource(intent)
            is MainContract.Intent.ClearSmsSources -> onClearSmsSources()
            is MainContract.Intent.ClearBankApplicationSources -> onClearBankApplicationSources()
            is MainContract.Intent.RefreshSourceFilters -> onRefreshSourceFilters()
        }
    }

    private fun onChangeToken(intent: MainContract.Intent.ChangeToken) {
        _state.value = state.value.copy(token = intent.token, isConnected = false)
        updateIsSavePossible()
    }

    private fun updateIsSavePossible() {
        viewModelScope.launch {
            val settingsData = settingsRepository.getSettings().first()
            _state.value = state.value.copy(isSavePossible = state.value.token != settingsData.token)
        }
    }

    private fun onSave() {
        viewModelScope.launch { connectAndPersist(state.value.token) }
    }

    private fun onChangeSearchQuery(intent: MainContract.Intent.ChangeSearchQuery) {
        _state.value = state.value.copy(searchQuery = intent.query)
        viewModelScope.launch { loadFirstPage() }
    }

    private fun onLoadMore() {
        if (state.value.isPageLoading || !state.value.canLoadMore) return
        viewModelScope.launch { loadNextPage() }
    }

    private fun onToggleAutomation() {
        viewModelScope.launch {
            val targetState: Boolean = toggleAutomationUseCase()
            _state.value = state.value.copy(isAutomationEnabled = targetState)
        }
    }

    private fun onReRegister(intent: MainContract.Intent.ReRegister) {
        viewModelScope.launch { connectAndPersist(intent.token) }
    }

    private fun onToggleSmsSource(intent: MainContract.Intent.ToggleSmsSource) {
        viewModelScope.launch {
            val normalizedSource: String = SourceFilter.normalizeSource(intent.sender)
            if (normalizedSource.isBlank()) return@launch
            settingsRepository.addKnownSmsSender(normalizedSource)
            val updatedSources: Set<String> = if (state.value.selectedSmsSenders.contains(normalizedSource)) {
                state.value.selectedSmsSenders - normalizedSource
            } else {
                state.value.selectedSmsSenders + normalizedSource
            }
            settingsRepository.saveSelectedSmsSenders(updatedSources)
            _state.value = state.value.copy(
                selectedSmsSenders = updatedSources,
                knownSmsSenders = state.value.knownSmsSenders + normalizedSource
            )
            updateAvailableSources()
        }
    }

    private fun onToggleBankApplicationSource(intent: MainContract.Intent.ToggleBankApplicationSource) {
        viewModelScope.launch {
            val normalizedSource: String = SourceFilter.normalizeSource(intent.packageName)
            if (normalizedSource.isBlank()) return@launch
            settingsRepository.addKnownBankApplication(normalizedSource)
            val updatedSources: Set<String> = if (state.value.selectedBankApplications.contains(normalizedSource)) {
                state.value.selectedBankApplications - normalizedSource
            } else {
                state.value.selectedBankApplications + normalizedSource
            }
            settingsRepository.saveSelectedBankApplications(updatedSources)
            _state.value = state.value.copy(
                selectedBankApplications = updatedSources,
                knownBankApplications = state.value.knownBankApplications + normalizedSource
            )
            updateAvailableSources()
        }
    }

    private fun onClearSmsSources() {
        viewModelScope.launch {
            settingsRepository.saveSelectedSmsSenders(emptySet())
            _state.value = state.value.copy(selectedSmsSenders = emptySet())
            updateAvailableSources()
        }
    }

    private fun onClearBankApplicationSources() {
        viewModelScope.launch {
            settingsRepository.saveSelectedBankApplications(emptySet())
            _state.value = state.value.copy(selectedBankApplications = emptySet())
            updateAvailableSources()
        }
    }

    private fun onRefreshSourceFilters() {
        viewModelScope.launch { refreshDeviceSourceLists() }
    }

    private suspend fun connectAndPersist(token: String) {
        _state.value = state.value.copy(token = token, isConnecting = true, errorMessage = null)
        val connectResult: Result<Unit> = connectDeviceUseCase(token)
        if (connectResult.isSuccess) {
            _state.value = state.value.copy(isConnected = true, isConnecting = false)
        } else {
            _state.value = state.value.copy(
                isConnected = false,
                isConnecting = false,
                errorMessage = connectResult.exceptionOrNull()?.message
            )
        }
        updateIsSavePossible()
    }

    private fun updateAvailableSources() {
        val smsSources: List<String> = SourceFilter.mergeKnownAndObserved(
            selectedSources = state.value.selectedSmsSenders,
            knownSources = state.value.knownSmsSenders,
            observedSources = historySmsSenders + deviceSmsSenders
        )
        val bankSources: List<String> = SourceFilter.mergeKnownAndObserved(
            selectedSources = state.value.selectedBankApplications,
            knownSources = state.value.knownBankApplications,
            observedSources = historyBankApplications + deviceInstalledApplications
        )
        _state.value = state.value.copy(
            availableSmsSenders = smsSources,
            availableBankApplications = bankSources
        )
    }

    private suspend fun refreshDeviceSourceLists() {
        deviceSmsSenders = notificationRepository.getDeviceSmsSenders()
        deviceInstalledApplications = notificationRepository.getInstalledApplicationPackages()
        updateAvailableSources()
    }

    private suspend fun loadFirstPage() {
        _state.value = state.value.copy(isPageLoading = true)
        val page = observeNotificationHistoryUseCase.getPage(
            query = state.value.searchQuery.ifBlank { null },
            limit = state.value.pageSize,
            offset = 0
        )
        val current = state.value.pagedNotifications
        if (current != page) {
            _state.value = state.value.copy(
                pagedNotifications = page,
                nextOffset = page.size,
                isPageLoading = false,
                canLoadMore = page.size >= state.value.pageSize
            )
        } else {
            _state.value = state.value.copy(
                isPageLoading = false,
                canLoadMore = page.size >= state.value.pageSize,
                nextOffset = page.size
            )
        }
    }

    private suspend fun loadNextPage() {
        _state.value = state.value.copy(isPageLoading = true)
        val page = observeNotificationHistoryUseCase.getPage(
            query = state.value.searchQuery.ifBlank { null },
            limit = state.value.pageSize,
            offset = state.value.nextOffset
        )
        val combined = state.value.pagedNotifications + page
        if (page.isNotEmpty() && combined != state.value.pagedNotifications) {
            _state.value = state.value.copy(
                pagedNotifications = combined,
                nextOffset = state.value.nextOffset + page.size,
                isPageLoading = false,
                canLoadMore = page.size >= state.value.pageSize
            )
        } else {
            _state.value = state.value.copy(
                isPageLoading = false,
                canLoadMore = page.size >= state.value.pageSize
            )
        }
    }

    private companion object {
        const val MILLIS_IN_SECOND: Long = 1000L
        const val PING_ELAPSED_UPDATE_DELAY_MS: Long = 1000L
    }
}
