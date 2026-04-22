package com.android.autopay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.autopay.utils.SourceFilter

private val ListHeight = 220.dp

@Composable
fun SourceSelectionList(
    options: List<String>,
    selectedSources: Set<String>,
    emptyStateText: String,
    onToggleSource: (String) -> Unit
) {
    if (options.isEmpty()) {
        Text(
            text = emptyStateText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(ListHeight)
            .verticalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            val isSelected: Boolean = selectedSources.any {
                SourceFilter.normalizeSource(it) == SourceFilter.normalizeSource(option)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSource(option) }
                )
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
