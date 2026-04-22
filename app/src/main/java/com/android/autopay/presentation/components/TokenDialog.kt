package com.android.autopay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun TokenDialog(
    initialToken: String,
    isConnecting: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newToken: String by remember { mutableStateOf(initialToken) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(newToken.trim())
                },
                enabled = newToken.isNotBlank() && !isConnecting
            ) {
                Text("Сменить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        title = { Text("Перерегистрировать устройство") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Введите новый токен, чтобы переподключить устройство.")
                OutlinedTextField(
                    value = newToken,
                    onValueChange = { newToken = it },
                    label = { Text("Новый токен") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmedToken: String = newToken.trim()
                        if (trimmedToken.isNotEmpty()) {
                            onConfirm(trimmedToken)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
