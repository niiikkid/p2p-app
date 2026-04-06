package com.android.autopay.presentation

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.autopay.data.DataStoreManager
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.SettingsData
import com.android.autopay.data.repositories.NotificationRepository
import com.android.autopay.data.utils.SourceFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStoreManager: DataStoreManager,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _state: MutableStateFlow<MainContract.State> = MutableStateFlow(MainContract.State())
    val state: StateFlow<MainContract.State> = _state.asStateFlow()
    private var historySmsSenders: List<String> = emptyList()
    private var historyBankApplications: List<String> = emptyList()
    private var deviceSmsSenders: List<String> = emptyList()
    private var deviceInstalledApplications: List<String> = emptyList()

    init {
        viewModelScope.launch {
            val settingsData = dataStoreManager.getSettings().first()
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
            dataStoreManager.getSettings().collect { settingsData ->
                _state.value = state.value.copy(
                    selectedSmsSenders = SourceFilter.normalizeSources(settingsData.selectedSmsSenders),
                    selectedBankApplications = SourceFilter.normalizeSources(settingsData.selectedBankApplications),
                    knownSmsSenders = SourceFilter.normalizeSources(settingsData.knownSmsSenders),
                    knownBankApplications = SourceFilter.normalizeSources(settingsData.knownBankApplications)
                )
                updateAvailableSources()
            }
        }
        viewModelScope.launch {
            refreshDeviceSourceLists()
        }

        viewModelScope.launch {
            notificationRepository.observeCount()
                .collect { totalCount ->
                    _state.value = state.value.copy(
                        notificationStats = state.value.notificationStats.copy(
                            receiveAll = totalCount
                        )
                    )
                    // При изменении количества записей в истории перезагружаем первую страницу логов,
                    // чтобы список обновлялся без перезапуска приложения.
                    loadFirstPage()
                }
        }

        viewModelScope.launch {
            notificationRepository.observeLatest(5)
                .collect { latest ->
                    _state.value = state.value.copy(
                        notificationStats = state.value.notificationStats.copy(
                            lastNotifications = latest
                        )
                    )
                    // Если меняются последние записи (в т.ч. статусы: отправлено/в очереди),
                    // перезагружаем первую страницу, чтобы актуализировать лог-лист.
                    loadFirstPage()
                }
        }
        viewModelScope.launch {
            notificationRepository.observeSmsSenders()
                .collect { senders ->
                    historySmsSenders = senders
                    updateAvailableSources()
                }
        }
        viewModelScope.launch {
            notificationRepository.observeBankApplications()
                .collect { applications ->
                    historyBankApplications = applications
                    updateAvailableSources()
                }
        }

        viewModelScope.launch { loadFirstPage() }

        // Следим за изменениями очереди на повторную отправку. Любое изменение влияет на статус в списке.
        viewModelScope.launch {
            notificationRepository.observeRetryQueueCount()
                .distinctUntilChanged()
                .collect {
                    loadFirstPage()
                }
        }

        viewModelScope.launch {
            val deviceName = Build.MODEL
            val manufacturer = Build.MANUFACTURER
            val model = Build.MODEL
            val androidVersion = Build.VERSION.RELEASE
            val apiLevel = Build.VERSION.SDK_INT
            val buildNumber = Build.DISPLAY
            val cpuAbi = Build.SUPPORTED_ABIS[0]

            _state.value = state.value.copy(
                deviceInfo = MainContract.State.DeviceInfo(
                    deviceName = deviceName,
                    manufacturer = manufacturer,
                    model = model,
                    androidVersion = androidVersion,
                    apiLevel = apiLevel.toString(),
                    buildNumber = buildNumber,
                    cpuAbi = cpuAbi
                )
            )
        }

        viewModelScope.launch {
            dataStoreManager.getSettings()
                .map { it.lastSuccessfulPingAt }
                .distinctUntilChanged()
                .collect { lastPingAt ->
                    _state.value = state.value.copy(lastSuccessfulPingAt = lastPingAt)
                }
        }

        viewModelScope.launch {
            while (true) {
                val elapsedSeconds: Long = if (state.value.lastSuccessfulPingAt == 0L) 0L else (System.currentTimeMillis() - state.value.lastSuccessfulPingAt) / 1000
                _state.value = state.value.copy(lastPingElapsedSeconds = elapsedSeconds)
                delay(1000)
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
        _state.value = state.value.copy(token = intent.token)
        updateIsSavePossible()

        _state.value = state.value.copy(
            isConnected = false
        )
    }

    private fun updateIsSavePossible() {
        viewModelScope.launch {
            val settingsData = dataStoreManager.getSettings().first()
            _state.value = state.value.copy(
                isSavePossible = state.value.token != settingsData.token
            )
        }
    }

    private fun onSave() {
        viewModelScope.launch {
            connectAndPersist(state.value.token)
        }
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
            val targetState: Boolean = !state.value.isAutomationEnabled
            dataStoreManager.saveAutomationEnabled(targetState)
            _state.value = state.value.copy(isAutomationEnabled = targetState)
        }
    }

    private fun onReRegister(intent: MainContract.Intent.ReRegister) {
        viewModelScope.launch {
            connectAndPersist(intent.token)
        }
    }
    private fun onToggleSmsSource(intent: MainContract.Intent.ToggleSmsSource) {
        viewModelScope.launch {
            val normalizedSource: String = SourceFilter.normalizeSource(intent.sender)
            if (normalizedSource.isBlank()) return@launch
            dataStoreManager.addKnownSmsSender(normalizedSource)
            val updatedSources: Set<String> = if (state.value.selectedSmsSenders.contains(normalizedSource)) {
                state.value.selectedSmsSenders - normalizedSource
            } else {
                state.value.selectedSmsSenders + normalizedSource
            }
            dataStoreManager.saveSelectedSmsSenders(updatedSources)
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
            dataStoreManager.addKnownBankApplication(normalizedSource)
            val updatedSources: Set<String> = if (state.value.selectedBankApplications.contains(normalizedSource)) {
                state.value.selectedBankApplications - normalizedSource
            } else {
                state.value.selectedBankApplications + normalizedSource
            }
            dataStoreManager.saveSelectedBankApplications(updatedSources)
            _state.value = state.value.copy(
                selectedBankApplications = updatedSources,
                knownBankApplications = state.value.knownBankApplications + normalizedSource
            )
            updateAvailableSources()
        }
    }
    private fun onClearSmsSources() {
        viewModelScope.launch {
            dataStoreManager.saveSelectedSmsSenders(emptySet())
            _state.value = state.value.copy(selectedSmsSenders = emptySet())
            updateAvailableSources()
        }
    }
    private fun onClearBankApplicationSources() {
        viewModelScope.launch {
            dataStoreManager.saveSelectedBankApplications(emptySet())
            _state.value = state.value.copy(selectedBankApplications = emptySet())
            updateAvailableSources()
        }
    }
    private fun onRefreshSourceFilters() {
        viewModelScope.launch {
            refreshDeviceSourceLists()
        }
    }

    private suspend fun connectAndPersist(token: String) {
        _state.value = state.value.copy(
            token = token,
            isConnecting = true,
            errorMessage = null
        )
        val currentSettings = dataStoreManager.getSettings().first()
        val connectResult = notificationRepository.connect(token)
        if (connectResult.isSuccess) {
            dataStoreManager.saveSettings(
                SettingsData(
                    token = token,
                    isConnected = true,
                    isAutomationEnabled = currentSettings.isAutomationEnabled,
                    lastSuccessfulPingAt = currentSettings.lastSuccessfulPingAt,
                    selectedSmsSenders = SourceFilter.normalizeSources(currentSettings.selectedSmsSenders),
                    selectedBankApplications = SourceFilter.normalizeSources(currentSettings.selectedBankApplications),
                    knownSmsSenders = SourceFilter.normalizeSources(currentSettings.knownSmsSenders),
                    knownBankApplications = SourceFilter.normalizeSources(currentSettings.knownBankApplications)
                )
            )
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
        val page = notificationRepository.getHistoryPage(
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
        val page = notificationRepository.getHistoryPage(
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
}

object MainContract {
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
        val pageSize: Int = 20,
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