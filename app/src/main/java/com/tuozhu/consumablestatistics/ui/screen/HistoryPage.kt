package com.tuozhu.consumablestatistics.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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

@Composable
fun HistoryPage(
    state: ConsumableUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (state.recentPrintTasks.isNotEmpty()) {
            item {
                PrintTaskHistorySummaryCard(
                    summary = state.printTaskSummary,
                    totalTasks = state.recentPrintTasks.size,
                )
            }
            item { RecentPrintTimelineSection(tasks = state.recentPrintTasks) }
            if (state.printHistory.isNotEmpty()) {
                item { ConfirmedPrintDigestSection(history = state.printHistory) }
            }
        }
        if (state.recentEvents.isNotEmpty()) {
            item {
                RecentEventSection(
                    events = state.recentEvents,
                    expandedInitially = state.recentPrintTasks.isEmpty(),
                )
            }
        } else if (state.recentPrintTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有历史记录", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "等你确认第一条打印任务，或手动记录一次耗材变动后，这里会开始沉淀打印历史和最近事件。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
