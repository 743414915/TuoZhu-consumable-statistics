package com.tuozhu.consumablestatistics.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuozhu.consumablestatistics.ui.ConsumableUiState
import com.tuozhu.consumablestatistics.ui.ConfirmedPrintDigestSection
import com.tuozhu.consumablestatistics.ui.PrintTaskHistorySummaryCard
import com.tuozhu.consumablestatistics.ui.RecentEventSection
import com.tuozhu.consumablestatistics.ui.RecentPrintTimelineSection
import com.tuozhu.consumablestatistics.ui.theme.BorderLight
import com.tuozhu.consumablestatistics.ui.theme.TextSecondary

@Composable
fun HistoryPage(
    state: ConsumableUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.recentPrintTasks.isNotEmpty()) {
            item { PrintTaskHistorySummaryCard(summary = state.printTaskSummary, totalTasks = state.recentPrintTasks.size) }
            item { RecentPrintTimelineSection(tasks = state.recentPrintTasks) }
            if (state.printHistory.isNotEmpty()) {
                item { ConfirmedPrintDigestSection(history = state.printHistory) }
            }
        }
        if (state.recentEvents.isNotEmpty()) {
            item { RecentEventSection(events = state.recentEvents, expandedInitially = state.recentPrintTasks.isEmpty()) }
        } else if (state.recentPrintTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderLight),
                ) {
                    Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有历史记录", style = MaterialTheme.typography.titleMedium)
                        Text("确认打印任务或手动记录耗材变动后，这里会沉淀历史。", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
    }
}
