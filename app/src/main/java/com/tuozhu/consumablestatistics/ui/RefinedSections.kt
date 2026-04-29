package com.tuozhu.consumablestatistics.ui

import android.graphics.Color.parseColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuozhu.consumablestatistics.data.PrintJobStatus
import com.tuozhu.consumablestatistics.ui.theme.ClayOrange
import com.tuozhu.consumablestatistics.ui.theme.MossInk
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SuccessMint
import kotlin.math.roundToInt

@Composable
fun FocusedActiveRollSection(
    roll: RollUiModel,
    onConsumeClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ClayOrange.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("当前活动卷", style = MaterialTheme.typography.titleLarge)
                    Text(roll.displayName, style = MaterialTheme.typography.titleMedium)
                }
                SectionPill(
                    text = if (roll.isLowStock) "建议校准" else "跟踪中",
                    containerColor = if (roll.isLowStock) SignalRed.copy(alpha = 0.12f) else SuccessMint.copy(alpha = 0.14f),
                    contentColor = if (roll.isLowStock) SignalRed else MossInk,
                )
            }
            Text(
                text = "${roll.material} | ${roll.brand}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "估算剩余 ${roll.estimatedRemainingGrams}g / ${roll.initialWeightGrams}g",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { roll.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50.dp)),
                color = if (roll.isLowStock) SignalRed else SuccessMint,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "${roll.calibrationLabel}，称重校准会更新这卷的计算基线。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onConsumeClick,
                    border = BorderStroke(1.dp, ClayOrange.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ClayOrange),
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("补记消耗")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onAdjustClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.Scale, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("更新称重基线")
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDeleteClick,
                border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SignalRed.copy(alpha = 0.05f),
                    contentColor = SignalRed,
                ),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("归档并移出当前库存")
            }
        }
    }
}

@Composable
fun InventoryRollCard(
    roll: RollUiModel,
    cardPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onConsumeClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSetActiveClick: () -> Unit,
    readOnly: Boolean = false,
) {
    val accent = when {
        readOnly -> MossInk
        roll.isLowStock -> SignalRed
        else -> colorFromHexCompat(roll.colorHex)
    }
    val progressColor = if (roll.isLowStock && !readOnly) SignalRed else SuccessMint

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardPadding),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.12f)))),
            )
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(colorFromHexCompat(roll.colorHex), CircleShape),
                        )
                        Column {
                            Text(
                                text = roll.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${roll.material} | ${roll.brand}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SectionPill(
                        text = when {
                            readOnly -> "已归档"
                            roll.isActive -> "活动卷"
                            roll.isLowStock -> "低余量"
                            else -> "待用"
                        },
                        containerColor = when {
                            readOnly -> MaterialTheme.colorScheme.secondaryContainer
                            roll.isActive -> ClayOrange.copy(alpha = 0.14f)
                            roll.isLowStock -> SignalRed.copy(alpha = 0.12f)
                            else -> SuccessMint.copy(alpha = 0.13f)
                        },
                        contentColor = when {
                            readOnly -> MaterialTheme.colorScheme.onSecondaryContainer
                            roll.isActive -> ClayOrange
                            roll.isLowStock -> SignalRed
                            else -> MossInk
                        },
                        icon = if (roll.isLowStock && !readOnly) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "估算剩余 ${roll.estimatedRemainingGrams}g / ${roll.initialWeightGrams}g",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        ) {
                            Text(
                                text = "${(roll.progress * 100).roundToInt()}%",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { roll.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50.dp)),
                        color = progressColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                Text(
                    text = roll.calibrationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (roll.notes.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Text(
                        text = roll.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (readOnly) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = "这卷已从当前库存移出，只保留历史追溯与校准参考，不再参与活动卷切换和扣减操作。",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = onConsumeClick,
                                border = BorderStroke(1.dp, ClayOrange.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClayOrange),
                            ) {
                                Icon(Icons.Outlined.Inventory2, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("补记消耗")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = onAdjustClick,
                                colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                            ) {
                                Icon(Icons.Outlined.Scale, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("校准余量")
                            }
                        }
                        if (!roll.isActive) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onSetActiveClick,
                                border = BorderStroke(1.dp, ClayOrange.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = ClayOrange.copy(alpha = 0.08f),
                                    contentColor = ClayOrange,
                                ),
                            ) {
                                Text("设为活动卷")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDeleteClick,
                            border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SignalRed.copy(alpha = 0.05f),
                                contentColor = SignalRed,
                            ),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (roll.isActive) "归档当前活动卷" else "归档此卷")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchivedRollsSection(
    rolls: List<RollUiModel>,
    expanded: Boolean,
    allowCollapse: Boolean,
    onToggleExpanded: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("已归档卷", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "共 ${rolls.size} 卷，已从当前库存移出，但历史和校准记录仍保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (allowCollapse) {
                    TextButton(onClick = onToggleExpanded) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (expanded) "收起" else "展开")
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rolls.forEachIndexed { index, roll ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        InventoryRollCard(
                            roll = roll,
                            cardPadding = 0.dp,
                            onConsumeClick = {},
                            onAdjustClick = {},
                            onDeleteClick = {},
                            onSetActiveClick = {},
                            readOnly = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrintTaskHistorySummaryCard(
    summary: PrintTaskHistorySummaryUiModel,
    totalTasks: Int,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("最近 $totalTasks 条桌面捕获任务", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "待确认 ${summary.pendingCount} · 已确认 ${summary.confirmedCount} · 最近已扣减 ${summary.confirmedUsageGrams}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionPill(
                text = "最近任务",
                containerColor = ClayOrange.copy(alpha = 0.12f),
                contentColor = ClayOrange,
            )
        }
    }
}

@Composable
fun RecentPrintTimelineSection(tasks: List<PrintTaskTimelineUiModel>) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = ClayOrange)
                Column {
                    Text("打印任务时间线", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "待确认和已确认任务共用一条时间线，优先用于核对桌面端同步结果。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            tasks.forEachIndexed { index, task ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                RecentPrintTimelineItem(task = task)
            }
        }
    }
}

@Composable
private fun RecentPrintTimelineItem(task: PrintTaskTimelineUiModel) {
    val statusColor = if (task.status == PrintJobStatus.CONFIRMED) MossInk else ClayOrange
    val statusContainer = if (task.status == PrintJobStatus.CONFIRMED) {
        SuccessMint.copy(alpha = 0.14f)
    } else {
        ClayOrange.copy(alpha = 0.14f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(task.modelName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (task.status == PrintJobStatus.CONFIRMED) {
                        "捕获 ${task.createdAtLabel} · 确认 ${task.updatedAtLabel}"
                    } else {
                        "捕获 ${task.createdAtLabel} · 等待确认"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionPill(
                text = task.statusLabel,
                containerColor = statusContainer,
                contentColor = statusColor,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionPill(
                text = "${task.estimatedUsageGrams}g",
                containerColor = ClayOrange.copy(alpha = 0.14f),
                contentColor = ClayOrange,
            )
            task.targetMaterial?.takeIf { it.isNotBlank() }?.let { material ->
                SectionPill(
                    text = material,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            SectionPill(
                text = task.sourceLabel,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = task.rollLabel?.let { "已关联到「$it」" } ?: "未关联耗材卷",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "任务 ID ${task.externalJobId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (task.note.isNotBlank()) {
            Text(
                text = task.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ConfirmedPrintDigestSection(history: List<PrintHistoryJobUiModel>) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("已确认打印摘要", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "最近 ${history.size} 条确认记录，便于快速复核已落库的打印任务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "查看")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    history.forEachIndexed { index, job ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(job.modelName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${job.confirmedAtLabel} · ${job.sourceLabel} · ${job.estimatedUsageGrams}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            job.targetMaterial?.takeIf { it.isNotBlank() }?.let { material ->
                                Text(
                                    text = "材料 $material",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            job.rollLabel?.let { rollLabel ->
                                Text(
                                    text = if (job.rollArchived) "关联卷 $rollLabel" else "扣减卷 $rollLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (job.note.isNotBlank()) {
                                Text(
                                    text = job.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentEventSection(
    events: List<RecentEventUiModel>,
    expandedInitially: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("库存事件", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "包含称重校准、手动补记等记录，不重复展示已确认打印扣减。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "查看")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    events.forEachIndexed { index, event ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${event.timestampLabel} · ${event.note}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun colorFromHexCompat(value: String): Color {
    return runCatching { Color(parseColor(value)) }.getOrElse { ClayOrange }
}

@Composable
private fun SectionPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: @Composable (() -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}
