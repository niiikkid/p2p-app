package com.android.autopay.presentation.screens

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    var showDeviceInfo: Boolean by remember { mutableStateOf(false) }
    var showTokenDialog: Boolean by remember { mutableStateOf(false) }
    var showSourceFiltersDialog: Boolean by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P2P Bridge") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundGrey)
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
                    showDeviceInfo = showDeviceInfo,
                    onToggleDeviceInfo = { showDeviceInfo = !showDeviceInfo },
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
        modifier = Modifier.padding(top = 80.dp, start = 16.dp, end = 16.dp)
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
    showDeviceInfo: Boolean,
    onToggleDeviceInfo: () -> Unit,
    onOpenTokenDialog: () -> Unit,
    onOpenSourceFiltersDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PingStatusRow(state = state)
        Spacer(modifier = Modifier.height(12.dp))
        ConnectionStatusCard(
            state = state,
            showDeviceInfo = showDeviceInfo,
            onToggleDeviceInfo = onToggleDeviceInfo
        )
        Spacer(modifier = Modifier.height(12.dp))
        TokenCard(state = state, onOpenTokenDialog = onOpenTokenDialog)
        Spacer(modifier = Modifier.height(12.dp))
        AutomationCard(
            state = state,
            onToggleAutomation = { onIntent(MainContract.Intent.ToggleAutomation) },
            onOpenSourceFiltersDialog = onOpenSourceFiltersDialog
        )
        Spacer(modifier = Modifier.height(12.dp))
        LogsCard(state = state, onIntent = onIntent)
    }
}

@Composable
private fun PingStatusRow(state: MainContract.State) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
        ) {
            Text(
                text = "Последний успешный пинг: ",
                style = MaterialTheme.typography.bodySmall
            )
            val seconds: Long = state.lastPingElapsedSeconds
            Text(
                text = if (state.lastSuccessfulPingAt == 0L) "—" else "$seconds c",
                style = MaterialTheme.typography.bodySmall,
                color = DarkGreen
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: MainContract.State,
    showDeviceInfo: Boolean,
    onToggleDeviceInfo: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                style = MaterialTheme.typography.titleSmall,
                color = statusColor
            )
            Button(
                onClick = onToggleDeviceInfo,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text(if (showDeviceInfo) "Скрыть доп. информацию" else "Показать доп. информацию")
            }
            if (showDeviceInfo) {
                Text(
                    text = stringResource(id = R.string.connection_info),
                    style = MaterialTheme.typography.titleSmall
                )
                DeviceInfoRowView(title = "Device: ", value = state.deviceInfo.deviceName)
                DeviceInfoRowView(title = "Manufacturer: ", value = state.deviceInfo.manufacturer)
                DeviceInfoRowView(title = "Model: ", value = state.deviceInfo.model)
                DeviceInfoRowView(title = "Android Version: ", value = state.deviceInfo.androidVersion)
                DeviceInfoRowView(title = "API Level: ", value = state.deviceInfo.apiLevel)
                DeviceInfoRowView(title = "Build Number: ", value = state.deviceInfo.buildNumber)
                DeviceInfoRowView(title = "CPU Architecture: ", value = state.deviceInfo.cpuAbi)
            }
        }
    }
}

@Composable
private fun TokenCard(
    state: MainContract.State,
    onOpenTokenDialog: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Текущий токен",
                style = MaterialTheme.typography.titleSmall,
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
private fun AutomationCard(
    state: MainContract.State,
    onToggleAutomation: () -> Unit,
    onOpenSourceFiltersDialog: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            val automationStatusText: String = if (state.isAutomationEnabled) "Автоматика включена" else "Автоматика выключена"
            val automationStatusColor: Color = if (state.isAutomationEnabled) DarkGreen else Color.Red
            Text(
                text = automationStatusText,
                style = MaterialTheme.typography.titleSmall,
                color = automationStatusColor
            )
            Button(
                onClick = onOpenSourceFiltersDialog,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Выбрать источники")
            }
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
            Button(
                onClick = onToggleAutomation,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(if (state.isAutomationEnabled) "Остановить автоматику" else "Запустить автоматику")
            }
        }
    }
}

@Composable
private fun LogsCard(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
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
                text = "Все логи",
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onIntent(MainContract.Intent.ChangeSearchQuery(it)) },
                label = { Text("Поиск по логам") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderGrey,
                    focusedBorderColor = AccentIndigo,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedTextColor = TextTertiary,
                    focusedTextColor = TextTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.pagedNotifications.forEach { notification ->
                    NotificationView(notification)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (state.canLoadMore) {
                Button(
                    onClick = { onIntent(MainContract.Intent.LoadMoreLogs) },
                    enabled = !state.isPageLoading,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) { Text("Загрузить ещё") }
            } else {
                Text(
                    text = "Это все логи",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun maskToken(token: String): String {
    val visibleCount: Int = TOKEN_VISIBLE_LENGTH * 2
    if (token.length <= visibleCount) return token
    val prefix: String = token.take(TOKEN_VISIBLE_LENGTH)
    val suffix: String = token.takeLast(TOKEN_VISIBLE_LENGTH)
    return "$prefix$TOKEN_MASK_PLACEHOLDER$suffix"
}
