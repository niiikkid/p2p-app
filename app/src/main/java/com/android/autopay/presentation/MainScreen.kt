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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.android.autopay.R
import com.android.autopay.data.models.Notification
import com.android.autopay.presentation.ui.theme.DarkGreen

@Composable
fun MainScreen() {
    val viewmodel: MainViewModel = hiltViewModel()
    val state by viewmodel.state.collectAsState()

    MainScreen(state, viewmodel::onIntent)
}

@Composable
private fun MainScreen(
    state: MainContract.State,
    onIntent: (MainContract.Intent) -> Unit
) {
    Scaffold(
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
                OutlinedTextField(
                    value = state.url,
                    onValueChange = { onIntent(MainContract.Intent.ChangeUrl(it)) },
                    label = { Text(stringResource(R.string.url)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = state.token,
                    onValueChange = { onIntent(MainContract.Intent.ChangeToken(it)) },
                    label = { Text(stringResource(R.string.token)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onIntent(MainContract.Intent.Save)
                    },
                    shape = MaterialTheme.shapes.extraSmall,
                    enabled = state.isSavePossible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(stringResource(R.string.connect))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.isConnected) {
                        Text(
                            text = stringResource(id = R.string.successfully_connected),
                            style = MaterialTheme.typography.titleSmall,
                            color = DarkGreen
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.not_connected),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Red
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.connection_info),
                        style = MaterialTheme.typography.titleSmall
                    )
                    DeviceInfoRowView(title = "Device: ", value = state.deviceInfo.deviceName)
                    DeviceInfoRowView(
                        title = "Manufacturer: ",
                        value = state.deviceInfo.manufacturer
                    )
                    DeviceInfoRowView(title = "Model: ", value = state.deviceInfo.model)
                    DeviceInfoRowView(
                        title = "Android Version: ",
                        value = state.deviceInfo.androidVersion
                    )
                    DeviceInfoRowView(title = "API Level: ", value = state.deviceInfo.apiLevel)
                    DeviceInfoRowView(
                        title = "Build Number: ",
                        value = state.deviceInfo.buildNumber
                    )
                    DeviceInfoRowView(
                        title = "CPU Architecture: ",
                        value = state.deviceInfo.cpuAbi
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Column {
                    Row {
                        Text(
                            text = "Получено всего: ",
                            fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                        )
                        Text(text = state.notificationStats.receiveAll.toString())
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.last_notifications),
                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                    )

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

@Composable
fun NotificationView(notification: Notification) {
    Column {
        Row {
            Text(
                text = stringResource(R.string.type_title),
                fontWeight = MaterialTheme.typography.titleMedium.fontWeight
            )
            Text(text = notification.type)
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