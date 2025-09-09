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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.android.autopay.R
import com.android.autopay.data.models.Notification
import com.android.autopay.data.models.NotificationType
import com.android.autopay.presentation.ui.theme.DarkGreen

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
    var showLogs by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AutoPay") })
        },
        modifier = Modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        var showTokenEditor by remember { mutableStateOf(false) }

                        Button(
                            onClick = { showTokenEditor = !showTokenEditor },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text("Обновить токен")
                        }

                        if (showTokenEditor) {
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = state.token,
                                onValueChange = { onIntent(MainContract.Intent.ChangeToken(it)) },
                                label = { Text("Токен") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { onIntent(MainContract.Intent.Save) },
                                shape = MaterialTheme.shapes.extraSmall,
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = state.errorMessage ?: "", color = Color.Red)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val statusText = if (state.isConnected) stringResource(id = R.string.successfully_connected) else stringResource(id = R.string.not_connected)
                        val statusColor = if (state.isConnected) DarkGreen else Color.Red

                        Text(text = statusText, style = MaterialTheme.typography.titleSmall, color = statusColor)

                        Button(
                            onClick = { showDeviceInfo = !showDeviceInfo },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) { Text(if (showDeviceInfo) "Скрыть доп. информацию" else "Показать доп. информацию") }

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

                Spacer(modifier = Modifier.height(20.dp))

                Card(colors = CardDefaults.cardColors()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row {
                            Text(
                                text = "Получено всего: ",
                                fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                            )
                            Text(text = state.notificationStats.receiveAll.toString())
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showLogs = !showLogs },
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) { Text(if (showLogs) "Скрыть логи" else "Показать логи") }

                        if (showLogs) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.allNotifications.asReversed().forEach { notification ->
                                    MinimalNotificationItem(notification)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.last_notifications),
                                fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.notificationStats.lastNotifications.forEach { notification ->
                                    NotificationView(notification)
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
    Column {
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