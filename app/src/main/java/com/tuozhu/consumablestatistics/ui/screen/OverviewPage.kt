package com.tuozhu.consumablestatistics.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuozhu.consumablestatistics.ui.ConsumableUiState
import com.tuozhu.consumablestatistics.ui.FocusedActiveRollSection
import com.tuozhu.consumablestatistics.ui.theme.BorderLight
import com.tuozhu.consumablestatistics.ui.theme.RedMuted
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SlateBlue
import com.tuozhu.consumablestatistics.ui.theme.SlateBlueDark
import com.tuozhu.consumablestatistics.ui.theme.SlateMuted
import com.tuozhu.consumablestatistics.ui.theme.SuccessGreen
import com.tuozhu.consumablestatistics.ui.theme.TextMuted
import com.tuozhu.consumablestatistics.ui.theme.TextSecondary

@Composable
fun OverviewPage(
    state: ConsumableUiState,
    modifier: Modifier = Modifier,
    onOpenSync: () -> Unit,
    onOpenHistory: () -> Unit,
    onConsumeClick: (Long) -> Unit,
    onAdjustClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Quick nav
        item { NavigatorCard(state, onOpenSync, onOpenHistory) }

        // Summary
        item { SnapshotCard(state) }

        // Active roll
        state.activeRoll?.let { roll ->
            item {
                FocusedActiveRollSection(
                    roll = roll,
                    onConsumeClick = { onConsumeClick(roll.id) },
                    onAdjustClick = { onAdjustClick(roll.id) },
                    onDeleteClick = { onDeleteClick(roll.id) },
                )
            }
        }
        if (state.activeRoll == null) {
            item { EmptyRollHint() }
        }
    }
}

@Composable
private fun NavigatorCard(
    state: ConsumableUiState,
    onOpenSync: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("快速入口", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NavShortcut(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Sync, null, tint = SlateBlue) },
                    title = "待确认打印",
                    value = "${state.pendingPrintJobs.size} 条",
                    hint = if (state.pendingPrintJobs.isEmpty()) "暂无待处理草稿" else "去同步页确认",
                    onClick = onOpenSync,
                )
                NavShortcut(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.History, null, tint = SlateBlueDark) },
                    title = "最近记录",
                    value = "${state.printHistory.size + state.recentEvents.size} 条",
                    hint = "查看历史与事件",
                    onClick = onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun NavShortcut(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    hint: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, color = SlateBlue)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun SnapshotCard(state: ConsumableUiState) {
    val lowStock = state.rolls.count { it.isLowStock }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("工作面摘要", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SnpChip(Modifier.weight(1f), "待确认", "${state.pendingPrintJobs.size} 条", SlateBlue, SlateMuted)
                SnpChip(Modifier.weight(1f), "低余量", "$lowStock 卷", if (lowStock > 0) SignalRed else SuccessGreen, if (lowStock > 0) RedMuted else androidx.compose.ui.graphics.Color(0xFFECFDF5))
                SnpChip(Modifier.weight(1f), "最近同步", state.syncStatus.lastSyncLabel, SlateBlueDark, SlateMuted)
            }
        }
    }
}

@Composable
private fun SnpChip(modifier: Modifier, label: String, value: String, accent: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = bg) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.8f))
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        }
    }
}

@Composable
private fun EmptyRollHint() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
    ) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("还没有耗材卷", style = MaterialTheme.typography.titleMedium)
            Text("点击右下角 + 添加第一卷耗材，然后去桌面端同步打印任务。", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
