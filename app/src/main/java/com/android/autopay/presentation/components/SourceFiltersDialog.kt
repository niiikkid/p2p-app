package com.android.autopay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

private const val TAB_INDEX_SMS: Int = 0
private const val TAB_INDEX_BANKS: Int = 1

@Composable
fun SourceFiltersDialog(
    availableSmsSenders: List<String>,
    selectedSmsSenders: Set<String>,
    availableBankApplications: List<String>,
    selectedBankApplications: Set<String>,
    onToggleSmsSource: (String) -> Unit,
    onToggleBankApplicationSource: (String) -> Unit,
    onClearSmsSources: () -> Unit,
    onClearBankApplicationSources: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab: Int by remember { mutableIntStateOf(TAB_INDEX_SMS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (selectedTab == TAB_INDEX_SMS) onClearSmsSources() else onClearBankApplicationSources()
                }
            ) {
                Text("Сбросить")
            }
        },
        title = { Text("Источники пересылки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == TAB_INDEX_SMS,
                        onClick = { selectedTab = TAB_INDEX_SMS },
                        text = { Text("Сообщения") }
                    )
                    Tab(
                        selected = selectedTab == TAB_INDEX_BANKS,
                        onClick = { selectedTab = TAB_INDEX_BANKS },
                        text = { Text("Банки") }
                    )
                }
                if (selectedTab == TAB_INDEX_SMS) {
                    SourceSelectionList(
                        options = availableSmsSenders,
                        selectedSources = selectedSmsSenders,
                        emptyStateText = "Список отправителей пока пуст. Если ничего не выбрано, отправляются все SMS.",
                        onToggleSource = onToggleSmsSource
                    )
                } else {
                    BankApplicationSelectionList(
                        packageNames = availableBankApplications,
                        selectedSources = selectedBankApplications,
                        emptyStateText = "Список приложений пока пуст. Если ничего не выбрано, отправляются все push.",
                        onToggleSource = onToggleBankApplicationSource
                    )
                }
            }
        }
    )
}
