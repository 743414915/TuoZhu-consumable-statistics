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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.data.SyncSourceType
import com.tuozhu.consumablestatistics.domain.SupportedMaterials
import com.tuozhu.consumablestatistics.ui.theme.BurntOrange
import com.tuozhu.consumablestatistics.ui.theme.ClayOrange
import com.tuozhu.consumablestatistics.ui.theme.ForestSlate
import com.tuozhu.consumablestatistics.ui.theme.IvoryMist
import com.tuozhu.consumablestatistics.ui.theme.MossInk
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SuccessMint
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumableApp(viewModel: ConsumableViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    var consumeRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var adjustRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var shelfExpandedOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }

    val shelfRolls = if (state.activeRoll == null) state.rolls else state.rolls.filterNot { it.id == state.activeRoll?.id }
    val allowShelfCollapse = shelfRolls.size > 2
    val shelfExpanded = if (allowShelfCollapse) shelfExpandedOverride ?: false else true

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(IvoryMist, Color(0xFFFFFBF6), Color(0xFFF3E4D6)),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("拓竹耗材管家", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = "A1 mini 无 AMS 场景",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { addDialogVisible = true },
                    containerColor = ClayOrange,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增耗材卷")
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    HeroSection(
                        totalRolls = state.rolls.size,
                        totalRemaining = state.rolls.sumOf { it.estimatedRemainingGrams },
                        lowStockCount = state.rolls.count { it.isLowStock },
                    )
                }
                item {
                    SyncStatusSection(
                        syncStatus = state.syncStatus,
                        onSaveEndpoint = viewModel::saveDesktopSyncAddress,
                        onPullSync = viewModel::pullSync,
                    )
                }
                if (state.pendingPrintJobs.isNotEmpty()) {
                    item {
                        PendingPrintJobsSection(
                            jobs = state.pendingPrintJobs,
                            activeRoll = state.activeRoll,
                            onConfirmJob = viewModel::confirmPrintJob,
                        )
                    }
                }
                state.activeRoll?.let { activeRoll ->
                    item {
                        ActiveRollSection(
                            roll = activeRoll,
                            onConsumeClick = { consumeRollId = activeRoll.id },
                            onAdjustClick = { adjustRollId = activeRoll.id },
                        )
                    }
                }
                if (state.rolls.isEmpty()) {
                    item { EmptyStateCard() }
                } else if (shelfRolls.isNotEmpty()) {
                    item {
                        InventoryShelfSection(
                            title = if (state.activeRoll == null) "全部耗材卷" else "其余耗材卷",
                            rolls = shelfRolls,
                            expanded = shelfExpanded,
                            onToggleExpanded = {
                                if (allowShelfCollapse) {
                                    shelfExpandedOverride = !shelfExpanded
                                }
                            },
                            allowCollapse = allowShelfCollapse,
                            onConsumeClick = { consumeRollId = it },
                            onAdjustClick = { adjustRollId = it },
                            onSetActiveClick = viewModel::setActiveRoll,
                        )
                    }
                }
                if (state.recentEvents.isNotEmpty()) {
                    item { RecentEventSection(events = state.recentEvents) }
                }
            }
        }
    }

    if (addDialogVisible) {
        AddRollDialog(
            onDismiss = { addDialogVisible = false },
            onConfirm = { input ->
                viewModel.addRoll(input)
                addDialogVisible = false
            },
        )
    }

    val consumeRoll = state.rolls.firstOrNull { it.id == consumeRollId }
    if (consumeRoll != null) {
        WeightChangeDialog(
            title = "登记耗材",
            confirmLabel = "保存",
            description = "记录本次打印大约消耗的克重，用于维持库存估算。",
            rollName = consumeRoll.displayName,
            initialValue = "",
            onDismiss = { consumeRollId = -1L },
            onConfirm = { grams, note ->
                viewModel.consumeRoll(consumeRoll.id, grams, note)
                consumeRollId = -1L
            },
        )
    }

    val adjustRoll = state.rolls.firstOrNull { it.id == adjustRollId }
    if (adjustRoll != null) {
        WeightChangeDialog(
            title = "校准余量",
            confirmLabel = "校准",
            description = "输入你刚刚实际称到的剩余克重，系统会把它作为新的基准值。",
            rollName = adjustRoll.displayName,
            initialValue = adjustRoll.estimatedRemainingGrams.toString(),
            allowZero = true,
            onDismiss = { adjustRollId = -1L },
            onConfirm = { grams, note ->
                viewModel.recalibrateRoll(adjustRoll.id, grams, note)
                adjustRollId = -1L
            },
        )
    }
}

@Composable
private fun HeroSection(totalRolls: Int, totalRemaining: Int, lowStockCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(colors = listOf(MossInk, ForestSlate, BurntOrange)))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "把每一卷耗材的真实余量留在眼前",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                    )
                    Text(
                        text = "库存仍以称重校准为最终基准，桌面同步负责把切片结果先送进待确认队列。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroMetric("卷数", totalRolls.toString())
                    HeroMetric("估算余量", "${totalRemaining}g")
                    HeroMetric("预警", lowStockCount.toString())
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String) {
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

@Composable
private fun SyncStatusSection(
    syncStatus: SyncStatusUiModel,
    onSaveEndpoint: (String) -> Unit,
    onPullSync: () -> Unit,
) {
    var settingsExpanded by rememberSaveable { mutableStateOf(!syncStatus.isConfigured) }
    var endpointInput by rememberSaveable { mutableStateOf(syncStatus.desktopBaseUrl) }

    LaunchedEffect(syncStatus.desktopBaseUrl) {
        endpointInput = syncStatus.desktopBaseUrl
        if (!syncStatus.isConfigured) {
            settingsExpanded = true
        }
    }

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
                    Text("桌面同步", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${syncStatus.source.toUiLabel()} | ${syncStatus.lastSyncLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = syncStatus.status.toUiLabel(),
                    containerColor = when (syncStatus.status) {
                        SyncConnectionStatus.IDLE -> ClayOrange.copy(alpha = 0.14f)
                        SyncConnectionStatus.SUCCESS -> SuccessMint.copy(alpha = 0.14f)
                        SyncConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant
                        SyncConnectionStatus.ERROR -> SignalRed.copy(alpha = 0.12f)
                    },
                    contentColor = when (syncStatus.status) {
                        SyncConnectionStatus.IDLE -> ClayOrange
                        SyncConnectionStatus.SUCCESS -> MossInk
                        SyncConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                        SyncConnectionStatus.ERROR -> SignalRed
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
            Text(
                text = syncStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lan,
                            contentDescription = null,
                            tint = ClayOrange,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (syncStatus.isConfigured) syncStatus.desktopBaseUrl else "尚未设置桌面地址",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "确保手机和电脑处于同一 Wi-Fi，并且电脑端已运行 desktop-agent/start-sync-server.ps1。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "待确认打印任务 ${syncStatus.pendingCount} 条",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                    Text(if (settingsExpanded) "收起设置" else "设置桌面地址")
                }
            }
            AnimatedVisibility(visible = settingsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("桌面同步地址") },
                        placeholder = { Text("例如 192.168.1.8:8823 或 http://192.168.1.8:8823") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onSaveEndpoint(endpointInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                        ) {
                            Text("保存地址")
                        }
                        TextButton(onClick = onPullSync) {
                            Text("立即拉取")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingPrintJobsSection(
    jobs: List<PendingPrintJobUiModel>,
    activeRoll: RollUiModel?,
    onConfirmJob: (Long) -> Unit,
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
            Text("待确认打印任务", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (activeRoll != null) {
                    "确认后会记入当前活动卷：${activeRoll.displayName}"
                } else {
                    "请先设置活动卷，再确认自动同步任务"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            jobs.forEachIndexed { index, job ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                PendingPrintJobCard(
                    job = job,
                    activeRoll = activeRoll,
                    onConfirm = { onConfirmJob(job.id) },
                )
            }
        }
    }
}

@Composable
private fun PendingPrintJobCard(
    job: PendingPrintJobUiModel,
    activeRoll: RollUiModel?,
    onConfirm: () -> Unit,
) {
    val requiredMaterial = SupportedMaterials.normalize(job.targetMaterial)
    val activeMaterial = SupportedMaterials.normalize(activeRoll?.material)
    val materialMatches = requiredMaterial == null || activeMaterial == requiredMaterial
    val actionHint = when {
        activeRoll == null -> "尚未选择活动卷"
        !materialMatches -> "当前活动卷为 ${activeRoll.material}，请先切换到 $requiredMaterial"
        else -> "将作用于 ${activeRoll.displayName}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(job.modelName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${job.sourceLabel} | ${job.createdAtLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = "${job.estimatedUsageGrams}g",
                containerColor = ClayOrange.copy(alpha = 0.14f),
                contentColor = ClayOrange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            job.targetMaterial?.let {
                StatusPill(
                    text = it,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (!materialMatches && activeRoll != null) {
                StatusPill(
                    text = "材料不匹配",
                    containerColor = SignalRed.copy(alpha = 0.12f),
                    contentColor = SignalRed,
                )
            }
        }
        Text(
            text = job.note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = actionHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = activeRoll != null && materialMatches,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
            ) {
                Text("确认并扣减")
            }
        }
    }
}

@Composable
private fun ActiveRollSection(
    roll: RollUiModel,
    onConsumeClick: () -> Unit,
    onAdjustClick: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("当前活动卷", style = MaterialTheme.typography.titleLarge)
                    Text(roll.displayName, style = MaterialTheme.typography.titleMedium)
                }
                StatusPill(
                    text = if (roll.isLowStock) "建议校准" else "追踪中",
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
                text = roll.calibrationLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConsumeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记录消耗")
                }
                TextButton(onClick = onAdjustClick) {
                    Icon(Icons.Outlined.Scale, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("称重校准")
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusPill(
                text = "从第一卷开始建档",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text("先录入你的第一卷耗材", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "当前仅保留 PLA Basic、PETG Basic、PLA Silk 三种材料。后续自动同步会围绕活动卷展开。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InventoryShelfSection(
    title: String,
    rolls: List<RollUiModel>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    allowCollapse: Boolean,
    onConsumeClick: (Long) -> Unit,
    onAdjustClick: (Long) -> Unit,
    onSetActiveClick: (Long) -> Unit,
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
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "共 ${rolls.size} 卷${if (!allowCollapse || expanded) "，当前已展开" else "，已收纳"}",
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
                        RollCard(
                            roll = roll,
                            cardPadding = 0.dp,
                            onConsumeClick = { onConsumeClick(roll.id) },
                            onAdjustClick = { onAdjustClick(roll.id) },
                            onSetActiveClick = { onSetActiveClick(roll.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RollCard(
    roll: RollUiModel,
    cardPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onConsumeClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onSetActiveClick: () -> Unit,
) {
    val accent = if (roll.isLowStock) SignalRed else colorFromHex(roll.colorHex)
    val progressColor = if (roll.isLowStock) SignalRed else SuccessMint

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = cardPadding),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.10f)))),
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
                                .background(colorFromHex(roll.colorHex), CircleShape),
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
                    StatusPill(
                        text = when {
                            roll.isActive -> "活动卷"
                            roll.isLowStock -> "低库存"
                            else -> "待用"
                        },
                        containerColor = when {
                            roll.isActive -> ClayOrange.copy(alpha = 0.14f)
                            roll.isLowStock -> SignalRed.copy(alpha = 0.12f)
                            else -> SuccessMint.copy(alpha = 0.13f)
                        },
                        contentColor = when {
                            roll.isActive -> ClayOrange
                            roll.isLowStock -> SignalRed
                            else -> MossInk
                        },
                        icon = if (roll.isLowStock) {
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

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConsumeClick,
                        colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                    ) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("记录消耗")
                    }
                    TextButton(onClick = onAdjustClick) {
                        Icon(Icons.Outlined.Scale, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("校准余量")
                    }
                    if (!roll.isActive) {
                        TextButton(onClick = onSetActiveClick) {
                            Text("设为活动卷")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: @Composable (() -> Unit)? = null,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

@Composable
private fun RecentEventSection(events: List<RecentEventUiModel>) {
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
            Text(text = "最近记录", style = MaterialTheme.typography.titleLarge)
            events.forEachIndexed { index, event ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${event.timestampLabel} | ${event.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun SyncConnectionStatus.toUiLabel(): String = when (this) {
    SyncConnectionStatus.IDLE -> "待接入"
    SyncConnectionStatus.SUCCESS -> "已同步"
    SyncConnectionStatus.OFFLINE -> "离线"
    SyncConnectionStatus.ERROR -> "失败"
}

private fun SyncSourceType.toUiLabel(): String = when (this) {
    SyncSourceType.MANUAL -> "手动"
    SyncSourceType.DESKTOP_AGENT -> "桌面同步"
    SyncSourceType.CLOUD -> "云同步"
}

private fun colorFromHex(value: String): Color {
    return runCatching { Color(parseColor(value)) }.getOrElse { ClayOrange }
}
