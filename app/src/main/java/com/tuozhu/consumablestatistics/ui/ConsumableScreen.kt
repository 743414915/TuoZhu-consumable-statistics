package com.tuozhu.consumablestatistics.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color.parseColor
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuozhu.consumablestatistics.data.PrintJobStatus
import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.data.SyncSourceType
import com.tuozhu.consumablestatistics.domain.SupportedMaterials
import com.tuozhu.consumablestatistics.sync.DesktopEndpointKind
import com.tuozhu.consumablestatistics.sync.connectionScopeHint
import com.tuozhu.consumablestatistics.sync.connectionScopeLabel
import com.tuozhu.consumablestatistics.sync.normalizeDesktopBaseUrl
import com.tuozhu.consumablestatistics.ui.theme.BurntOrange
import com.tuozhu.consumablestatistics.ui.theme.ClayOrange
import com.tuozhu.consumablestatistics.ui.theme.ForestSlate
import com.tuozhu.consumablestatistics.ui.theme.IvoryMist
import com.tuozhu.consumablestatistics.ui.theme.MossInk
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SuccessMint
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.math.roundToInt

private enum class ConsumableRootPage(
    val title: String,
    val subtitle: String,
) {
    OVERVIEW("总览", "今天先看活动卷、库存风险和待处理同步"),
    INVENTORY("卷库", "集中管理活动卷和其他耗材卷"),
    SYNC("同步", "处理桌面同步、扫码配对和待确认打印"),
    HISTORY("记录", "查看打印历史和最近事件"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumableApp(viewModel: ConsumableViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    var consumeRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var adjustRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var deleteRollId by rememberSaveable { mutableLongStateOf(-1L) }
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
                        onDiscoverLan = viewModel::autoDiscoverLanDesktop,
                        onScanPairing = viewModel::pairWithScannedDesktopAddress,
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
                if (state.printHistory.isNotEmpty()) {
                    item {
                        PrintHistorySection(history = state.printHistory)
                    }
                }
                state.activeRoll?.let { activeRoll ->
                    item {
                        ActiveRollSection(
                            roll = activeRoll,
                            onConsumeClick = { consumeRollId = activeRoll.id },
                            onAdjustClick = { adjustRollId = activeRoll.id },
                            onDeleteClick = { deleteRollId = activeRoll.id },
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
                            onDeleteClick = { deleteRollId = it },
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

    val deleteRoll = state.rolls.firstOrNull { it.id == deleteRollId }
    if (deleteRoll != null) {
        DeleteRollDialog(
            rollName = deleteRoll.displayName,
            isActive = deleteRoll.isActive,
            onDismiss = { deleteRollId = -1L },
            onConfirm = {
                viewModel.deleteRoll(deleteRoll.id)
                deleteRollId = -1L
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumableWorkspaceApp(viewModel: ConsumableViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var addDialogVisible by rememberSaveable { mutableStateOf(false) }
    var consumeRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var adjustRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var deleteRollId by rememberSaveable { mutableLongStateOf(-1L) }
    var shelfExpandedOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var currentPage by rememberSaveable { mutableStateOf(ConsumableRootPage.OVERVIEW) }

    val shelfRolls = if (state.activeRoll == null) state.rolls else state.rolls.filterNot { it.id == state.activeRoll?.id }
    val allowShelfCollapse = shelfRolls.size > 2
    val shelfExpanded = if (allowShelfCollapse) shelfExpandedOverride ?: false else true
    val showAddFab = currentPage == ConsumableRootPage.OVERVIEW || currentPage == ConsumableRootPage.INVENTORY

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
                            Text(currentPage.title, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = currentPage.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                if (showAddFab) {
                    FloatingActionButton(
                        onClick = { addDialogVisible = true },
                        containerColor = ClayOrange,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "新增耗材卷")
                    }
                }
            },
            bottomBar = {
                BottomTabBar(
                    currentPage = currentPage,
                    onSelectPage = { currentPage = it },
                )
            },
        ) { innerPadding ->
            when (currentPage) {
                ConsumableRootPage.OVERVIEW -> OverviewPage(
                    state = state,
                    modifier = Modifier.padding(innerPadding),
                    onOpenSync = { currentPage = ConsumableRootPage.SYNC },
                    onOpenHistory = { currentPage = ConsumableRootPage.HISTORY },
                    onConsumeClick = { consumeRollId = it },
                    onAdjustClick = { adjustRollId = it },
                    onDeleteClick = { deleteRollId = it },
                )
                ConsumableRootPage.INVENTORY -> InventoryPage(
                    state = state,
                    shelfRolls = shelfRolls,
                    shelfExpanded = shelfExpanded,
                    allowShelfCollapse = allowShelfCollapse,
                    modifier = Modifier.padding(innerPadding),
                    onToggleShelfExpanded = {
                        if (allowShelfCollapse) {
                            shelfExpandedOverride = !shelfExpanded
                        }
                    },
                    onConsumeClick = { consumeRollId = it },
                    onAdjustClick = { adjustRollId = it },
                    onDeleteClick = { deleteRollId = it },
                    onSetActiveClick = viewModel::setActiveRoll,
                )
                ConsumableRootPage.SYNC -> SyncPage(
                    state = state,
                    modifier = Modifier.padding(innerPadding),
                    onSaveEndpoint = viewModel::saveDesktopSyncAddress,
                    onPullSync = viewModel::pullSync,
                    onDiscoverLan = viewModel::autoDiscoverLanDesktop,
                    onScanPairing = viewModel::pairWithScannedDesktopAddress,
                    onConfirmJob = viewModel::confirmPrintJob,
                )
                ConsumableRootPage.HISTORY -> HistoryPage(
                    state = state,
                    modifier = Modifier.padding(innerPadding),
                )
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
            title = "记录耗材",
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

    val deleteRoll = state.rolls.firstOrNull { it.id == deleteRollId }
    if (deleteRoll != null) {
        DeleteRollDialog(
            rollName = deleteRoll.displayName,
            isActive = deleteRoll.isActive,
            onDismiss = { deleteRollId = -1L },
            onConfirm = {
                viewModel.deleteRoll(deleteRoll.id)
                deleteRollId = -1L
            },
        )
    }
}

@Composable
private fun BottomTabBar(
    currentPage: ConsumableRootPage,
    onSelectPage: (ConsumableRootPage) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BottomTabButton(
                modifier = Modifier.weight(1f),
                selected = currentPage == ConsumableRootPage.OVERVIEW,
                label = ConsumableRootPage.OVERVIEW.title,
                icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                onClick = { onSelectPage(ConsumableRootPage.OVERVIEW) },
            )
            BottomTabButton(
                modifier = Modifier.weight(1f),
                selected = currentPage == ConsumableRootPage.INVENTORY,
                label = ConsumableRootPage.INVENTORY.title,
                icon = { Icon(Icons.Outlined.Inventory2, contentDescription = null) },
                onClick = { onSelectPage(ConsumableRootPage.INVENTORY) },
            )
            BottomTabButton(
                modifier = Modifier.weight(1f),
                selected = currentPage == ConsumableRootPage.SYNC,
                label = ConsumableRootPage.SYNC.title,
                icon = { Icon(Icons.Outlined.Sync, contentDescription = null) },
                onClick = { onSelectPage(ConsumableRootPage.SYNC) },
            )
            BottomTabButton(
                modifier = Modifier.weight(1f),
                selected = currentPage == ConsumableRootPage.HISTORY,
                label = ConsumableRootPage.HISTORY.title,
                icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                onClick = { onSelectPage(ConsumableRootPage.HISTORY) },
            )
        }
    }
}

@Composable
private fun BottomTabButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) ClayOrange.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (selected) ClayOrange else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ConsumableNavItem(
    currentPage: ConsumableRootPage,
    targetPage: ConsumableRootPage,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    BottomTabButton(
        selected = currentPage == targetPage,
        label = targetPage.title,
        onClick = onClick,
        icon = icon,
    )
}

@Composable
private fun PageColumn(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun OverviewPage(
    state: ConsumableUiState,
    modifier: Modifier = Modifier,
    onOpenSync: () -> Unit,
    onOpenHistory: () -> Unit,
    onConsumeClick: (Long) -> Unit,
    onAdjustClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
) {
    PageColumn(modifier = modifier) {
        item {
            HeroSection(
                totalRolls = state.rolls.size,
                totalRemaining = state.rolls.sumOf { it.estimatedRemainingGrams },
                lowStockCount = state.rolls.count { it.isLowStock },
            )
        }
        item {
            OverviewNavigatorCard(
                state = state,
                onOpenSync = onOpenSync,
                onOpenHistory = onOpenHistory,
            )
        }
        state.activeRoll?.let { activeRoll ->
            item {
                ActiveRollSection(
                    roll = activeRoll,
                    onConsumeClick = { onConsumeClick(activeRoll.id) },
                    onAdjustClick = { onAdjustClick(activeRoll.id) },
                    onDeleteClick = { onDeleteClick(activeRoll.id) },
                )
            }
        }
        if (state.activeRoll == null) {
            item { EmptyStateCard() }
        }
    }
}

@Composable
private fun InventoryPage(
    state: ConsumableUiState,
    shelfRolls: List<RollUiModel>,
    shelfExpanded: Boolean,
    allowShelfCollapse: Boolean,
    modifier: Modifier = Modifier,
    onToggleShelfExpanded: () -> Unit,
    onConsumeClick: (Long) -> Unit,
    onAdjustClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onSetActiveClick: (Long) -> Unit,
) {
    PageColumn(modifier = modifier) {
        state.activeRoll?.let { activeRoll ->
            item {
                ActiveRollSection(
                    roll = activeRoll,
                    onConsumeClick = { onConsumeClick(activeRoll.id) },
                    onAdjustClick = { onAdjustClick(activeRoll.id) },
                    onDeleteClick = { onDeleteClick(activeRoll.id) },
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
                    onToggleExpanded = onToggleShelfExpanded,
                    allowCollapse = allowShelfCollapse,
                    onConsumeClick = onConsumeClick,
                    onAdjustClick = onAdjustClick,
                    onDeleteClick = onDeleteClick,
                    onSetActiveClick = onSetActiveClick,
                )
            }
        }
    }
}

@Composable
private fun SyncPage(
    state: ConsumableUiState,
    modifier: Modifier = Modifier,
    onSaveEndpoint: (String) -> Unit,
    onPullSync: () -> Unit,
    onDiscoverLan: () -> Unit,
    onScanPairing: (String?) -> Unit,
    onConfirmJob: (Long) -> Unit,
) {
    PageColumn(modifier = modifier) {
        item {
            SyncStatusSection(
                syncStatus = state.syncStatus,
                onSaveEndpoint = onSaveEndpoint,
                onPullSync = onPullSync,
                onDiscoverLan = onDiscoverLan,
                onScanPairing = onScanPairing,
            )
        }
        if (state.pendingPrintJobs.isNotEmpty()) {
            item {
                PendingPrintJobsSection(
                    jobs = state.pendingPrintJobs,
                    activeRoll = state.activeRoll,
                    onConfirmJob = onConfirmJob,
                )
            }
        } else {
            item { SyncEmptyCard() }
        }
    }
}

@Composable
private fun HistoryPage(
    state: ConsumableUiState,
    modifier: Modifier = Modifier,
) {
    PageColumn(modifier = modifier) {
        if (state.recentPrintTasks.isNotEmpty()) {
            item { PrintTaskHistorySummaryCard(summary = state.printTaskSummary) }
            item { PrintTaskTimelineSection(tasks = state.recentPrintTasks, expandedInitially = true) }
        }
        if (state.recentEvents.isNotEmpty()) {
            item { RecentEventSection(events = state.recentEvents) }
        } else if (state.recentPrintTasks.isEmpty()) {
            item { HistoryEmptyCard() }
        }
    }
}

@Composable
private fun OverviewNavigatorCard(
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
                text = "把高频操作分流到不同页面后，这里只保留最值得先看的状态。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OverviewShortcut(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Sync, contentDescription = null, tint = ClayOrange) },
                    title = "待确认打印",
                    value = "${state.pendingPrintJobs.size} 条",
                    hint = if (state.pendingPrintJobs.isEmpty()) "当前没有待处理草稿" else "去同步页确认打印任务",
                    onClick = onOpenSync,
                )
                OverviewShortcut(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.History, contentDescription = null, tint = MossInk) },
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
private fun OverviewShortcut(
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
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncEmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("当前没有待确认打印任务", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "同步页现在只保留桌面连接和打印确认相关操作。若桌面端已切片完成，点击“立即拉取”即可刷新。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryEmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("还没有历史记录", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "等你确认第一条打印任务，或手动记录一次耗材变动后，这里会开始沉淀打印历史和最近事件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    onDiscoverLan: () -> Unit,
    onScanPairing: (String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var settingsExpanded by rememberSaveable { mutableStateOf(!syncStatus.isConfigured) }
    var endpointInput by rememberSaveable { mutableStateOf(syncStatus.desktopBaseUrl) }
    val normalizedInput = normalizeDesktopBaseUrl(endpointInput)
    val hasPendingChanges = normalizedInput != syncStatus.desktopBaseUrl
    val syncBusy = syncStatus.isPulling || syncStatus.isDiscoveringLan
    val pullButtonLabel = when {
        syncStatus.isPulling -> "正在拉取桌面记录..."
        hasPendingChanges -> "先保存地址再拉取"
        syncStatus.isConfigured -> "立即拉取桌面打印记录"
        else -> "先填写桌面地址"
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val rawValue = result.contents
        if (rawValue.isNullOrBlank()) {
            Toast.makeText(context, "已取消扫码", Toast.LENGTH_SHORT).show()
        } else {
            onScanPairing(rawValue)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scanLauncher.launch(buildQrScanOptions())
        } else {
            val permanentlyDenied = activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            if (permanentlyDenied) {
                Toast.makeText(context, "相机权限已被禁止，请在系统设置中开启后再扫码", Toast.LENGTH_LONG).show()
                openAppSettings(context)
            } else {
                Toast.makeText(context, "未授予相机权限，请改用手动填写地址", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                        text = "来源 ${syncStatus.source.toUiLabel()} · ${syncStatus.lastSyncLabel}",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "当前已绑定的桌面地址",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Computer,
                            contentDescription = null,
                            tint = ClayOrange,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (syncStatus.isConfigured) syncStatus.desktopBaseUrl else "正在检测或尚未设置桌面同步地址",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = "地址类型 ${syncStatus.addressKindLabel}",
                            containerColor = when (syncStatus.addressKind) {
                                DesktopEndpointKind.TAILSCALE,
                                DesktopEndpointKind.MAGIC_DNS -> ClayOrange.copy(alpha = 0.12f)
                                DesktopEndpointKind.LAN -> MaterialTheme.colorScheme.secondaryContainer
                                DesktopEndpointKind.CUSTOM -> MaterialTheme.colorScheme.tertiaryContainer
                                DesktopEndpointKind.NONE -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = when (syncStatus.addressKind) {
                                DesktopEndpointKind.TAILSCALE,
                                DesktopEndpointKind.MAGIC_DNS -> ClayOrange
                                DesktopEndpointKind.LAN -> MaterialTheme.colorScheme.onSecondaryContainer
                                DesktopEndpointKind.CUSTOM -> MaterialTheme.colorScheme.onTertiaryContainer
                                DesktopEndpointKind.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            icon = {
                                Icon(
                                    imageVector = when (syncStatus.addressKind) {
                                        DesktopEndpointKind.TAILSCALE,
                                        DesktopEndpointKind.MAGIC_DNS -> Icons.Outlined.Public
                                        DesktopEndpointKind.LAN -> Icons.Outlined.Lan
                                        else -> Icons.Outlined.Computer
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                        )
                        StatusPill(
                            text = syncStatus.addressKind.connectionScopeLabel(),
                            containerColor = if (
                                syncStatus.addressKind == DesktopEndpointKind.TAILSCALE ||
                                syncStatus.addressKind == DesktopEndpointKind.MAGIC_DNS
                            ) {
                                SuccessMint.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (
                                syncStatus.addressKind == DesktopEndpointKind.TAILSCALE ||
                                syncStatus.addressKind == DesktopEndpointKind.MAGIC_DNS
                            ) {
                                MossInk
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = syncStatus.addressKind.connectionScopeHint(),
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
                StatusPill(
                    text = "待确认打印任务 ${syncStatus.pendingCount} 条",
                    containerColor = if (syncStatus.pendingCount > 0) ClayOrange.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (syncStatus.pendingCount > 0) ClayOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    if (hasPendingChanges || !syncStatus.isConfigured) {
                        settingsExpanded = true
                    } else {
                        onPullSync()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !syncBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClayOrange,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(pullButtonLabel)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (activity == null) {
                            Toast.makeText(context, "当前页面无法启动扫码器", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val permissionGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (permissionGranted) {
                            scanLauncher.launch(buildQrScanOptions())
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !syncBusy,
                ) {
                    Text("扫码配对")
                }
                OutlinedButton(
                    onClick = { settingsExpanded = !settingsExpanded },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, ClayOrange.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ClayOrange),
                ) {
                    Icon(
                        imageVector = if (settingsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (settingsExpanded) "收起地址设置" else if (syncStatus.isConfigured) "编辑地址设置" else "填写桌面地址")
                }
            }
            AnimatedVisibility(visible = settingsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "支持直接输入 Tailscale IP、MagicDNS 域名，或局域网 IP。未填写协议时会自动补成 http://",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("桌面同步地址") },
                        placeholder = { Text("例如 100.x.x.x:8823、电脑名.tailnet.ts.net:8823 或 192.168.x.x:8823") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !syncBusy,
                    )
                    if (hasPendingChanges) {
                        Text(
                            text = "地址有未保存改动。立即拉取仍会使用上一次已保存的地址。",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClayOrange,
                        )
                    }
                    if (syncStatus.isDiscoveringLan) {
                        Text(
                            text = "正在扫描同一 Wi‑Fi 下的桌面同步服务，通常需要几秒钟。",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClayOrange,
                        )
                    } else {
                        Text(
                            text = "“扫描同一 Wi‑Fi”只用于局域网备用地址；如果你已经接入 Tailscale，请优先使用桌面端主推荐地址或二维码配对。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSaveEndpoint(endpointInput)
                                if (normalizedInput.isNotBlank()) {
                                    settingsExpanded = false
                                }
                            },
                            enabled = !syncBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                        ) {
                            Text(if (normalizedInput.isBlank()) "清空地址" else "保存地址")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDiscoverLan,
                            enabled = !syncBusy,
                        ) {
                            Text(if (syncStatus.isDiscoveringLan) "扫描同一 Wi‑Fi..." else "扫描同一 Wi‑Fi")
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
        job.isConfirming -> "正在提交确认，请稍候"
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
                enabled = activeRoll != null && materialMatches && !job.isConfirming,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onConsumeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记录消耗")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onAdjustClick,
                ) {
                    Icon(Icons.Outlined.Scale, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("称重校准")
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
                Text("删除当前活动卷")
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
    onDeleteClick: (Long) -> Unit,
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
                            onDeleteClick = { onDeleteClick(roll.id) },
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
    onDeleteClick: () -> Unit,
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

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onConsumeClick,
                            colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
                        ) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("记录消耗")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onAdjustClick,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
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
                        Text(if (roll.isActive) "删除当前活动卷" else "删除此卷")
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

private fun buildQrScanOptions(): ScanOptions {
    return ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("请扫描桌面端配对二维码")
        setBeepEnabled(false)
        setOrientationLocked(true)
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Composable
private fun PrintHistorySection(
    history: List<PrintHistoryJobUiModel>,
    expandedInitially: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }

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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = ClayOrange)
                    Column {
                        Text("打印历史", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "最近 ${history.size} 条已确认打印",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                                text = "${job.confirmedAtLabel} | ${job.sourceLabel} | ${job.estimatedUsageGrams}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            job.targetMaterial?.takeIf { it.isNotBlank() }?.let { material ->
                                Text(
                                    text = "材料：$material",
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
private fun PrintTaskHistorySummaryCard(summary: PrintTaskHistorySummaryUiModel) {
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
            Text("桌面捕获摘要", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "这里集中看最近自动捕获到的打印任务，待确认和已确认会放在同一条时间线里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HistoryMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "待确认",
                    value = "${summary.pendingCount} 条",
                    accent = ClayOrange,
                )
                HistoryMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "已确认",
                    value = "${summary.confirmedCount} 条",
                    accent = MossInk,
                )
                HistoryMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "最近消耗",
                    value = "${summary.confirmedUsageGrams}g",
                    accent = SuccessMint,
                )
            }
        }
    }
}

@Composable
private fun HistoryMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = accent.copy(alpha = 0.12f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = accent)
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun PrintTaskTimelineSection(
    tasks: List<PrintTaskTimelineUiModel>,
    expandedInitially: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(expandedInitially) }

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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = ClayOrange)
                    Column {
                        Text("打印任务时间线", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "最近 ${tasks.size} 条桌面捕获任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "查看")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tasks.forEachIndexed { index, task ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                        PrintTaskTimelineItem(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintTaskTimelineItem(task: PrintTaskTimelineUiModel) {
    val statusColor = if (task.status == PrintJobStatus.CONFIRMED) MossInk else ClayOrange
    val statusContainer = if (task.status == PrintJobStatus.CONFIRMED) {
        SuccessMint.copy(alpha = 0.14f)
    } else {
        ClayOrange.copy(alpha = 0.14f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.modelName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${task.updatedAtPrefix} ${task.updatedAtLabel} | ${task.sourceLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(
                text = task.statusLabel,
                containerColor = statusContainer,
                contentColor = statusColor,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = "${task.estimatedUsageGrams}g",
                containerColor = ClayOrange.copy(alpha = 0.14f),
                contentColor = ClayOrange,
            )
            task.targetMaterial?.takeIf { it.isNotBlank() }?.let { material ->
                StatusPill(
                    text = material,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        task.rollLabel?.let { rollLabel ->
            Text(
                text = "关联耗材卷：$rollLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "捕获时间 ${task.createdAtLabel} | 任务 ID ${task.externalJobId}",
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
    SyncConnectionStatus.IDLE -> "待连接"
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
