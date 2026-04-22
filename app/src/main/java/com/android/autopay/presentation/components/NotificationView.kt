package com.android.autopay.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.autopay.R
import com.android.autopay.domain.models.Notification
import com.android.autopay.presentation.theme.BorderGrey
import com.android.autopay.presentation.theme.DarkGreen
import com.android.autopay.presentation.theme.StatusError
import com.android.autopay.presentation.theme.StatusWarning
import com.android.autopay.presentation.theme.TextMuted
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LogTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

@Composable
fun NotificationView(notification: Notification) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(width = 1.dp, color = BorderGrey),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val createdAtText: String = formatTimestamp(notification.timestamp)
            val statusColor: Color = when {
                notification.sentAt > 0L -> DarkGreen
                notification.queuedForRetry -> StatusWarning
                else -> StatusError
            }
            val statusText: String = when {
                notification.sentAt > 0L -> "Отправлено"
                notification.queuedForRetry -> "В очереди на повтор"
                else -> "Не отправлено"
            }
            val sentAtText: String? = if (notification.sentAt > 0L) formatTimestamp(notification.sentAt) else null
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
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                    if (sentAtText != null) {
                        Text(
                            text = "Отправлено: $sentAtText",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Text(
                        text = "Создано: $createdAtText",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    return Instant.ofEpochMilli(timestampMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(LogTimestampFormatter)
}
