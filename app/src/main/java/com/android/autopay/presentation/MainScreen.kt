package com.android.autopay.presentation

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.Snackbar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.android.autopay.R
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType
import com.android.autopay.presentation.ui.theme.DarkGreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.shadow

@Composable
fun MainScreen() {
    val viewmodel: MainViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()

    MainScreen(state, viewmodel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    var showDeviceInfo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoPay") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF3F4F6))
            )
        },
        modifier = Modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (!state.isConnected) {
                var showConnectForm by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!showConnectForm) {
                        Button(
                            onClick = { showConnectForm = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Подключить приложение")
                        }
                    } else {
                        OutlinedTextField(
                            value = state.token,
                            onValueChange = { onIntent(MainContract.Intent.ChangeToken(it)) },
                            label = { Text("Токен") },
                            shape = RoundedCornerShape(12.dp),
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
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                    Text("Подключаем...")
                                }
                            } else {
                                Text("Сохранить")
                            }
                        }
                        if (state.errorMessage != null) {
                            Text(text = state.errorMessage ?: "", color = Color.Red)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                        ) {
                            Text(text = "Последний успешный пинг: ", style = MaterialTheme.typography.bodySmall)
                            val seconds = state.lastPingElapsedSeconds
                            Text(text = if (state.lastSuccessfulPingAt == 0L) "—" else "$seconds c", style = MaterialTheme.typography.bodySmall, color = DarkGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statusText = if (state.isConnected)
                                stringResource(id = R.string.successfully_connected)
                            else
                                stringResource(id = R.string.not_connected)

                            val statusColor = if (state.isConnected) DarkGreen else Color.Red

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.titleSmall,
                                color = statusColor
                            )

                            Button(
                                onClick = { showDeviceInfo = !showDeviceInfo },
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
                    Spacer(modifier = Modifier.height(12.dp))
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
                            Spacer(modifier = Modifier.height(0.dp))
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = { onIntent(MainContract.Intent.ChangeSearchQuery(it)) },
                                label = { Text("Поиск по логам") },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color(0xFFE5E7EB),
                                    focusedBorderColor = Color(0xFF6366F1),
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    unfocusedTextColor = Color(0xFF374151),
                                    focusedTextColor = Color(0xFF374151)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.pagedNotifications.forEach { notification ->
                                    NotificationView(notification)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (state.isPageLoading) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                if (state.canLoadMore) {
                                    Button(
                                        onClick = { onIntent(MainContract.Intent.LoadMoreLogs) },
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
                }
            }
        }
    }
}

@Composable
fun NotificationView(notification: Notification) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = Color(0xFFE5E7EB)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row {
                Text(
                    text = stringResource(R.string.type_title),
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Text(text = notification.type.wireName)
            }
            Row {
                Text(
                    text = stringResource(R.string.from_title),
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Text(text = notification.sender)
            }
            Row {
                Text(
                    text = stringResource(R.string.message_title),
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                )
                Text(text = notification.message)
            }
        }
    }
}

@Composable
fun DeviceInfoRowView(title: String, value: String) {
    Row {
        Text(
            text = title,
            fontWeight = MaterialTheme.typography.titleMedium.fontWeight
        )
        Text(text = value)
    }
}

@Composable
fun MinimalNotificationItem(notification: Notification) {
    Column(modifier = Modifier
        .fillMaxWidth()
    ) {
        Row {
            Text(text = notification.type.wireName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = notification.sender, style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = notification.message, style = MaterialTheme.typography.bodySmall)
    }
}