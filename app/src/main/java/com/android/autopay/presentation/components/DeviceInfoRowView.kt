package com.android.autopay.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

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
