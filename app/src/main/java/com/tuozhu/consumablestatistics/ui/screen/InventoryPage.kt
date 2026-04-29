package com.tuozhu.consumablestatistics.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuozhu.consumablestatistics.ui.ArchivedRollsSection
import com.tuozhu.consumablestatistics.ui.ConsumableUiState
import com.tuozhu.consumablestatistics.ui.FocusedActiveRollSection
import com.tuozhu.consumablestatistics.ui.InventoryRollCard
import com.tuozhu.consumablestatistics.ui.RollUiModel
import com.tuozhu.consumablestatistics.ui.theme.ClayOrange

@Composable
fun InventoryPage(
    state: ConsumableUiState,
    shelfRolls: List<RollUiModel>,
    shelfExpanded: Boolean,
    allowShelfCollapse: Boolean,
    archivedExpanded: Boolean,
    modifier: Modifier = Modifier,
    onToggleShelfExpanded: () -> Unit,
    onToggleArchivedExpanded: () -> Unit,
    onConsumeClick: (Long) -> Unit,
    onAdjustClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onSetActiveClick: (Long) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
        if (state.rolls.isEmpty()) {
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
                        Text("还没有耗材卷", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "点击右下角 + 添加第一卷耗材，开始管理库存。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (shelfRolls.isNotEmpty()) {
            item {
                ShelfCard(
                    title = if (state.activeRoll == null) "全部耗材卷" else "其余耗材卷",
                    rolls = shelfRolls,
                    expanded = shelfExpanded,
                    allowCollapse = allowShelfCollapse,
                    onToggleExpanded = onToggleShelfExpanded,
                    onConsumeClick = onConsumeClick,
                    onAdjustClick = onAdjustClick,
                    onDeleteClick = onDeleteClick,
                    onSetActiveClick = onSetActiveClick,
                )
            }
        }
        if (state.archivedRolls.isNotEmpty()) {
            item {
                ArchivedRollsSection(
                    rolls = state.archivedRolls,
                    expanded = archivedExpanded,
                    allowCollapse = state.archivedRolls.size > 1,
                    onToggleExpanded = onToggleArchivedExpanded,
                )
            }
        }
        item { BackupCard(onExport, onImport) }
    }
}

@Composable
private fun BackupCard(onExport: () -> Unit, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("数据备份", style = MaterialTheme.typography.titleLarge)
            Text(
                "导出完整备份（含事件与打印记录）或从其他设备导入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Upload, null, tint = ClayOrange)
                    Text(" 导出", color = ClayOrange)
                }
                TextButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Download, null, tint = ClayOrange)
                    Text(" 导入", color = ClayOrange)
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(
    title: String,
    rolls: List<RollUiModel>,
    expanded: Boolean,
    allowCollapse: Boolean,
    onToggleExpanded: () -> Unit,
    onConsumeClick: (Long) -> Unit,
    onAdjustClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onSetActiveClick: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text("共 ${rolls.size} 卷", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (allowCollapse) {
                    TextButton(onClick = onToggleExpanded) {
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        Text(if (expanded) "收起" else "展开")
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rolls.forEachIndexed { i, roll ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        InventoryRollCard(
                            roll = roll,
                            cardPadding = 0.dp,
                            onConsumeClick = { onConsumeClick(roll.id) },
                            onAdjustClick = { onAdjustClick(roll.id) },
                            onDeleteClick = { onDeleteClick(roll.id) },
                            onSetActiveClick = { onSetActiveClick(roll.id) },
                        )
                    }
                }
            }
        }
    }
}
