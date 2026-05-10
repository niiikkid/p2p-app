package com.android.autopay.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.autopay.domain.models.Notification
import com.android.autopay.domain.models.NotificationContentStatus

@Composable
fun MinimalNotificationItem(notification: Notification) {
    val messageText: String = if (notification.contentStatus == NotificationContentStatus.HIDDEN_BY_ANDROID_OR_APP) {
        HIDDEN_NOTIFICATION_MESSAGE
    } else {
        notification.message
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row {
            Text(
                text = notification.type.wireName,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = notification.sender,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = messageText,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private const val HIDDEN_NOTIFICATION_MESSAGE: String =
    "Содержимое уведомления скрыто Android или приложением банка. Оригинальный текст не был передан системе уведомлений."
