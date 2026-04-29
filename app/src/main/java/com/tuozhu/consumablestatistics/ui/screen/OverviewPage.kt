package com.tuozhu.consumablestatistics.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tuozhu.consumablestatistics.ui.ConsumableUiState
import com.tuozhu.consumablestatistics.ui.FocusedActiveRollSection
import com.tuozhu.consumablestatistics.ui.RollUiModel
import com.tuozhu.consumablestatistics.ui.theme.BurntOrange
import com.tuozhu.consumablestatistics.ui.theme.ClayOrange
import com.tuozhu.consumablestatistics.ui.theme.ForestSlate
import com.tuozhu.consumablestatistics.ui.theme.MossInk
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SuccessMint

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
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeroCard(state) }
        item { NavigatorCard(state, onOpenSync, onOpenHistory) }
        item { SnapshotCard(state) }
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

// ── Hero ──

@Composable
private fun HeroCard(state: ConsumableUiState) {
    val lowStock = state.rolls.count { it.isLowStock }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(MossInk, ForestSlate, BurntOrange)))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "把每一卷耗材的真实余量留在眼前",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                    Text(
                        "库存仍以称重校准为最终基准，桌面同步负责把切片结果先送进待确认队列。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroChip("卷数", "${state.rolls.size}")
                    HeroChip("估算余量", "${state.rolls.sumOf { it.estimatedRemainingGrams }}g")
                    HeroChip("预警", "$lowStock")
                }
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.13f)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.74f))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
    }
}

// ── Navigator ──

@Composable
private fun NavigatorCard(
    state: ConsumableUiState,
    onOpenSync: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("快速入口", style = MaterialTheme.typography.titleLarge)
            Text(
                "把高频操作分流到不同页面后，这里只保留最值得先看的状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NavShortcut(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Sync, null, tint = ClayOrange) },
                    title = "待确认打印",
                    value = "${state.pendingPrintJobs.size} 条",
                    hint = if (state.pendingPrintJobs.isEmpty()) "当前没有待处理草稿" else "去同步页确认打印任务",
                    onClick = onOpenSync,
                )
                NavShortcut(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.History, null, tint = MossInk) },
                    title = "最近记录",
                    value = "${state.printHistory.size + state.recentEvents.size} 条",
                    hint = "去记录页查看历史和事件",
                    onClick = onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun NavShortcut(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    hint: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Snapshot ──

@Composable
private fun SnapshotCard(state: ConsumableUiState) {
    val lowStock = state.rolls.count { it.isLowStock }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("工作面摘要", style = MaterialTheme.typography.titleLarge)
            Text(
                "首页只保留今天最值得先处理的同步、库存和历史信号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SnapshotChip(Modifier.weight(1f), "待确认", "${state.pendingPrintJobs.size} 条", ClayOrange)
                SnapshotChip(Modifier.weight(1f), "低余量", "$lowStock 卷", if (lowStock > 0) SignalRed else SuccessMint)
                SnapshotChip(Modifier.weight(1f), "最近同步", state.syncStatus.lastSyncLabel, MossInk)
            }
        }
    }
}

@Composable
private fun SnapshotChip(modifier: Modifier, label: String, value: String, accent: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = accent.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        }
    }
}

// ── Empty ──

@Composable
private fun EmptyRollHint() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("还没有耗材卷", style = MaterialTheme.typography.titleLarge)
            Text(
                "点击右下角 + 添加第一卷耗材，然后去桌面端同步打印任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
