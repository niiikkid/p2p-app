package com.android.autopay.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.android.autopay.BuildConfig
import com.android.autopay.R
import com.android.autopay.presentation.components.DeviceInfoRowView
import com.android.autopay.presentation.components.NotificationView
import com.android.autopay.presentation.components.SourceFiltersDialog
import com.android.autopay.presentation.components.TokenDialog
import com.android.autopay.presentation.theme.AccentIndigo
import com.android.autopay.presentation.theme.BackgroundGrey
import com.android.autopay.presentation.theme.BorderGrey
import com.android.autopay.presentation.theme.DarkGreen
import com.android.autopay.presentation.theme.TextPrimary
import com.android.autopay.presentation.theme.TextSecondary
import com.android.autopay.presentation.theme.TextTertiary
import com.android.autopay.presentation.viewmodels.MainContract
import com.android.autopay.presentation.viewmodels.MainViewModel

private const val TOKEN_VISIBLE_LENGTH: Int = 4
private const val TOKEN_MASK_PLACEHOLDER: String = "..."
private val CardCornerRadius = 20.dp
private val SectionSpacing = 16.dp

private enum class MainScreenSection(val title: String) {
    DASHBOARD("Панель управления"),
    DEVICE_INFO("Информация об устройстве"),
    TOKEN("Изменить токен"),
    SOURCES("Источники сообщений"),
    LOGS("Логи SMS")
}

@Composable
fun MainScreen() {
    val viewModel: MainViewModel = hiltViewModel()
    val state: MainContract.State by viewModel.state.collectAsState()
    MainScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    var showTokenDialog: Boolean by remember { mutableStateOf(false) }
    var showSourceFiltersDialog: Boolean by remember { mutableStateOf(false) }
    var showMenu: Boolean by remember { mutableStateOf(false) }
    var selectedSection: MainScreenSection by rememberSaveable {
        mutableStateOf(MainScreenSection.DASHBOARD)
    }
    Scaffold(
        containerColor = BackgroundGrey,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = BuildConfig.APP_TITLE
                        )
                        if (state.isConnected) {
                            Text(
                                text = selectedSection.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    if (state.isConnected) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_menu),
                                    contentDescription = "Открыть меню",
                                    tint = TextPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                MainScreenSection.entries.forEach { section ->
                                    DropdownMenuItem(
                                        text = { Text(section.title) },
                                        onClick = {
                                            showMenu = false
                                            if (section == MainScreenSection.SOURCES) {
                                                onIntent(MainContract.Intent.RefreshSourceFilters)
                                            }
                                            selectedSection = section
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundGrey,
                    actionIconContentColor = TextPrimary,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (showTokenDialog) {
                TokenDialog(
                    initialToken = state.token,
                    isConnecting = state.isConnecting,
                    onConfirm = { newToken ->
                        onIntent(MainContract.Intent.ReRegister(newToken))
                        showTokenDialog = false
                    },
                    onDismiss = { showTokenDialog = false }
                )
            }
            if (showSourceFiltersDialog) {
                SourceFiltersDialog(
                    availableSmsSenders = state.availableSmsSenders,
                    selectedSmsSenders = state.selectedSmsSenders,
                    availableBankApplications = state.availableBankApplications,
                    selectedBankApplications = state.selectedBankApplications,
                    onToggleSmsSource = { onIntent(MainContract.Intent.ToggleSmsSource(it)) },
                    onToggleBankApplicationSource = { onIntent(MainContract.Intent.ToggleBankApplicationSource(it)) },
                    onClearSmsSources = { onIntent(MainContract.Intent.ClearSmsSources) },
                    onClearBankApplicationSources = { onIntent(MainContract.Intent.ClearBankApplicationSources) },
                    onDismiss = { showSourceFiltersDialog = false }
                )
            }
            if (!state.isConnected) {
                ConnectForm(state = state, onIntent = onIntent)
            } else {
                ConnectedContent(
                    state = state,
                    onIntent = onIntent,
                    selectedSection = selectedSection,
                    onOpenTokenDialog = { showTokenDialog = true },
                    onOpenSourceFiltersDialog = {
                        onIntent(MainContract.Intent.RefreshSourceFilters)
                        showSourceFiltersDialog = true
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectForm(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    var showConnectForm: Boolean by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 104.dp, start = 16.dp, end = 16.dp)
    ) {
        if (!showConnectForm) {
            Button(
                onClick = { showConnectForm = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Подключить устройство")
            }
        } else {
            OutlinedTextField(
                value = state.token,
                onValueChange = { onIntent(MainContract.Intent.ChangeToken(it)) },
                label = { Text("Токен") },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(MainContract.Intent.Save) }),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onIntent(MainContract.Intent.Save) },
                shape = RoundedCornerShape(12.dp),
                enabled = state.isSavePossible && !state.isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (state.isConnecting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Подключаем...")
                    }
                } else {
                    Text("Сохранить")
                }
            }
            if (state.errorMessage != null) {
                Text(text = state.errorMessage, color = Color.Red)
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit,
    selectedSection: MainScreenSection,
    onOpenTokenDialog: () -> Unit,
    onOpenSourceFiltersDialog: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp)
    ) {
        when (selectedSection) {
            MainScreenSection.DASHBOARD -> DashboardSection(
                state = state,
                onIntent = onIntent
            )
            MainScreenSection.DEVICE_INFO -> DeviceInfoSection(state = state)
            MainScreenSection.TOKEN -> TokenSection(
                state = state,
                onOpenTokenDialog = onOpenTokenDialog
            )
            MainScreenSection.SOURCES -> SourcesSection(
                state = state,
                onOpenSourceFiltersDialog = onOpenSourceFiltersDialog
            )
            MainScreenSection.LOGS -> LogsSection(
                state = state,
                onIntent = onIntent
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: MainContract.State,
    showDeviceInfo: Boolean
) {
    AppSectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            val statusText: String = if (state.isConnected) {
                stringResource(id = R.string.successfully_connected)
            } else {
                stringResource(id = R.string.not_connected)
            }
            val statusColor: Color = if (state.isConnected) DarkGreen else Color.Red
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = statusColor
            )
            if (showDeviceInfo) {
                Text(
                    text = "Сведения об устройстве",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                DeviceInfoRowView(title = "Устройство: ", value = state.deviceInfo.deviceName)
                DeviceInfoRowView(title = "Производитель: ", value = state.deviceInfo.manufacturer)
                DeviceInfoRowView(title = "Модель: ", value = state.deviceInfo.model)
                DeviceInfoRowView(title = "Версия Android: ", value = state.deviceInfo.androidVersion)
                DeviceInfoRowView(title = "API Level: ", value = state.deviceInfo.apiLevel)
                DeviceInfoRowView(title = "Номер сборки: ", value = state.deviceInfo.buildNumber)
                DeviceInfoRowView(title = "Архитектура CPU: ", value = state.deviceInfo.cpuAbi)
            }
        }
    }
}

@Composable
private fun DashboardSection(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        DashboardSummaryCard(state = state)
        Spacer(modifier = Modifier.height(SectionSpacing))
        AutomationToggleCard(
            isAutomationEnabled = state.isAutomationEnabled,
            isAppVersionSupported = state.isAppVersionSupported,
            onToggleAutomation = { onIntent(MainContract.Intent.ToggleAutomation) }
        )
    }
}

@Composable
private fun DeviceInfoSection(state: MainContract.State) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ConnectionStatusCard(
            state = state,
            showDeviceInfo = true
        )
    }
}

@Composable
private fun TokenSection(
    state: MainContract.State,
    onOpenTokenDialog: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        TokenCard(state = state, onOpenTokenDialog = onOpenTokenDialog)
    }
}

@Composable
private fun SourcesSection(
    state: MainContract.State,
    onOpenSourceFiltersDialog: () -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SourceSummaryCard(state = state)
        Spacer(modifier = Modifier.height(SectionSpacing))
        Button(
            onClick = onOpenSourceFiltersDialog,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Text("Выбрать источники")
        }
    }
}

@Composable
private fun LogsSection(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        LogsCard(state = state, onIntent = onIntent)
    }
}

@Composable
private fun DashboardSummaryCard(state: MainContract.State) {
    AppSectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp)
        ) {
            DashboardMetricRow(
                label = "Статус подключения",
                value = if (state.isConnected) "Подключено" else "Не подключено",
                valueColor = if (state.isConnected) DarkGreen else Color.Red
            )
            DashboardMetricRow(
                label = "Последний успешный пинг",
                value = if (state.lastSuccessfulPingAt == 0L) "—" else "${state.lastPingElapsedSeconds} c назад",
                valueColor = TextPrimary
            )
            DashboardMetricRow(
                label = "Получено сообщений",
                value = state.notificationStats.receiveAll.toString(),
                valueColor = TextPrimary
            )
            DashboardMetricRow(
                label = "Автоматика",
                value = if (state.isAutomationEnabled) "Включена" else "Выключена",
                valueColor = if (state.isAutomationEnabled) DarkGreen else Color.Red
            )
            if (!state.isAppVersionSupported) {
                Text(
                    text = "Ваше приложение устарело. Обновите приложение, чтобы снова включить автоматику.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun AutomationToggleCard(
    isAutomationEnabled: Boolean,
    isAppVersionSupported: Boolean,
    onToggleAutomation: () -> Unit
) {
    AppSectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            val automationStatusText: String = if (isAutomationEnabled) "Автоматика включена" else "Автоматика выключена"
            val automationStatusColor: Color = if (isAutomationEnabled) DarkGreen else Color.Red
            Text(
                text = automationStatusText,
                style = MaterialTheme.typography.titleMedium,
                color = automationStatusColor
            )
            if (!isAppVersionSupported) {
                Text(
                    text = "Ваше приложение устарело и его надо обновить.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red
                )
            }
            Button(
                onClick = onToggleAutomation,
                enabled = isAppVersionSupported,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(if (isAutomationEnabled) "Отключить автоматику" else "Включить автоматику")
            }
        }
    }
}

@Composable
private fun SourceSummaryCard(state: MainContract.State) {
    AppSectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Какие сообщения обрабатывать",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = if (state.selectedSmsSenders.isEmpty()) "SMS: все отправители" else "SMS: выбрано ${state.selectedSmsSenders.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = if (state.selectedBankApplications.isEmpty()) "Банки: все приложения" else "Банки: выбрано ${state.selectedBankApplications.size}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TokenCard(
    state: MainContract.State,
    onOpenTokenDialog: () -> Unit
) {
    AppSectionCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Текущий токен устройства",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = maskToken(state.token),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                IconButton(
                    onClick = onOpenTokenDialog,
                    enabled = !state.isConnecting
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_token),
                        contentDescription = "Изменить токен",
                        tint = TextPrimary
                    )
                }
            }
            if (state.isConnecting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .height(18.dp)
                            .width(18.dp)
                    )
                    Text(
                        text = "Обновляем токен...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun LogsCard(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    AppSectionCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row {
                Text(
                    text = "Получено всего: ",
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Text(text = state.notificationStats.receiveAll.toString())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Логи SMS",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onIntent(MainContract.Intent.ChangeSearchQuery(it)) },
                label = { Text("Поиск по SMS-логам") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderGrey,
                    focusedBorderColor = AccentIndigo,
                    unfocusedContainerColor = BackgroundGrey,
                    focusedContainerColor = Color.White,
                    unfocusedTextColor = TextTertiary,
                    focusedTextColor = TextTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Найдено сообщений: ${state.filteredNotificationsCount}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.pagedNotifications.isEmpty()) {
                    Text(
                        text = if (state.searchQuery.isBlank()) {
                            "Логи пока отсутствуют"
                        } else {
                            "По вашему запросу ничего не найдено"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    state.pagedNotifications.forEach { notification ->
                        NotificationView(notification)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.isPageLoading) {
                Text(
                    text = "Загружаем страницу...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            } else if (state.filteredNotificationsCount > 0) {
                LogsPaginationControls(
                    state = state,
                    onIntent = onIntent
                )
            }
        }
    }
}

@Composable
private fun LogsPaginationControls(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Страница ${state.currentLogsPage} из ${state.totalLogsPages}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { onIntent(MainContract.Intent.GoToLogsPage(state.currentLogsPage - 1)) },
                enabled = state.currentLogsPage > 1,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Назад")
            }
            Button(
                onClick = { onIntent(MainContract.Intent.GoToLogsPage(state.currentLogsPage + 1)) },
                enabled = state.currentLogsPage < state.totalLogsPages,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Вперёд")
            }
        }
    }
}

@Composable
private fun AppSectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(width = 1.dp, color = BorderGrey),
        shape = RoundedCornerShape(CardCornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        content()
    }
}

@Composable
private fun DashboardMetricRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

private fun maskToken(token: String): String {
    val visibleCount: Int = TOKEN_VISIBLE_LENGTH * 2
    if (token.length <= visibleCount) return token
    val prefix: String = token.take(TOKEN_VISIBLE_LENGTH)
    val suffix: String = token.takeLast(TOKEN_VISIBLE_LENGTH)
    return "$prefix$TOKEN_MASK_PLACEHOLDER$suffix"
}
