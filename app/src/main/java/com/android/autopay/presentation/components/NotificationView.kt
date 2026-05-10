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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.android.autopay.R
import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationContentHiddenReason
import com.android.autopay.domain.models.NotificationContentStatus
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
    val clipboardManager = LocalClipboardManager.current
    var isDebugInfoExpanded: Boolean by remember(notification.idempotencyKey) { mutableStateOf(false) }
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
            val isContentHidden: Boolean = notification.contentStatus == NotificationContentStatus.HIDDEN_BY_ANDROID_OR_APP
            val messageText: String = if (isContentHidden) HIDDEN_NOTIFICATION_MESSAGE else notification.message
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
                Text(text = messageText)
            }
            if (isContentHidden && notification.hiddenReason != null) {
                Text(
                    text = "Причина: ${notification.hiddenReason.toReadableText()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusWarning
                )
            }
            notification.debugInfo?.takeIf { debugInfo -> debugInfo.isNotBlank() }?.let { debugInfo ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Отладка уведомления",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(debugInfo))
                            }
                        ) {
                            Text("Скопировать")
                        }
                        TextButton(
                            onClick = {
                                isDebugInfoExpanded = !isDebugInfoExpanded
                            }
                        ) {
                            Text(if (isDebugInfoExpanded) "Свернуть" else "Развернуть")
                        }
                    }
                }
                if (isDebugInfoExpanded) {
                    Text(
                        text = debugInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
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

private fun NotificationContentHiddenReason.toReadableText(): String {
    return when (this) {
        NotificationContentHiddenReason.VISIBILITY_PRIVATE -> "уведомление помечено как приватное"
        NotificationContentHiddenReason.VISIBILITY_SECRET -> "уведомление помечено как секретное"
        NotificationContentHiddenReason.PUBLIC_VERSION_ONLY -> "доступна только публичная версия уведомления"
        NotificationContentHiddenReason.EMPTY_EXTRAS -> "в extras нет текста уведомления"
        NotificationContentHiddenReason.UNKNOWN -> "реальный текст не найден в Notification API"
    }
}

private const val HIDDEN_NOTIFICATION_MESSAGE: String =
    "Содержимое уведомления скрыто Android или приложением банка. Оригинальный текст не был передан системе уведомлений."
