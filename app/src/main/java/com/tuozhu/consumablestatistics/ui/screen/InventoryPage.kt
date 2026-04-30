package com.tuozhu.consumablestatistics.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import com.tuozhu.consumablestatistics.ui.theme.BorderLight
import com.tuozhu.consumablestatistics.ui.theme.SlateBlue
import com.tuozhu.consumablestatistics.ui.theme.TextSecondary

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
    onManageMaterials: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderLight),
                ) {
                    Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有耗材卷", style = MaterialTheme.typography.titleMedium)
                        Text("点击右下角 + 添加第一卷耗材。", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        }
        if (shelfRolls.isNotEmpty()) {
            item { ShelfCard(shelfRolls, if (state.activeRoll == null) "全部耗材卷" else "其余耗材卷", shelfExpanded, allowShelfCollapse, onToggleShelfExpanded, onConsumeClick, onAdjustClick, onDeleteClick, onSetActiveClick) }
        }
        if (state.archivedRolls.isNotEmpty()) {
            item { ArchivedRollsSection(rolls = state.archivedRolls, expanded = archivedExpanded, allowCollapse = state.archivedRolls.size > 1, onToggleExpanded = onToggleArchivedExpanded) }
        }
        item { MaterialManageCard(onManageMaterials) }
        item { BackupCard(onExport, onImport) }
    }
}

@Composable
private fun MaterialManageCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("自定义材料", style = MaterialTheme.typography.titleMedium)
            Text("添加和管理你自己的耗材材料类型，会出现在新增耗材卷的材料选项中。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            TextButton(onClick = onClick) { Text("管理材料") }
        }
    }
}

@Composable
private fun ShelfCard(
    rolls: List<RollUiModel>,
    title: String,
    expanded: Boolean,
    allowCollapse: Boolean,
    onToggle: () -> Unit,
    onConsume: (Long) -> Unit,
    onAdjust: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSetActive: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("共 ${rolls.size} 卷", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                if (allowCollapse) {
                    TextButton(onClick = onToggle) {
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                        Text(if (expanded) "收起" else "展开")
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rolls.forEachIndexed { i, roll ->
                        if (i > 0) HorizontalDivider(color = BorderLight)
                        InventoryRollCard(
                            roll = roll, cardPadding = 0.dp,
                            onConsumeClick = { onConsume(roll.id) },
                            onAdjustClick = { onAdjust(roll.id) },
                            onDeleteClick = { onDelete(roll.id) },
                            onSetActiveClick = { onSetActive(roll.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupCard(onExport: () -> Unit, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("数据备份", style = MaterialTheme.typography.titleMedium)
            Text("导出完整备份（含事件与打印记录）或从其他设备导入。", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            HorizontalDivider(color = BorderLight)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Upload, null, tint = SlateBlue); Spacer(Modifier.width(4.dp)); Text("导出", color = SlateBlue)
                }
                TextButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Download, null, tint = SlateBlue); Spacer(Modifier.width(4.dp)); Text("导入", color = SlateBlue)
                }
            }
        }
    }
}
