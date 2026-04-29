package com.tuozhu.consumablestatistics.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuozhu.consumablestatistics.ui.screen.HistoryPage
import com.tuozhu.consumablestatistics.ui.screen.InventoryPage
import com.tuozhu.consumablestatistics.ui.screen.OverviewPage
import com.tuozhu.consumablestatistics.ui.screen.SyncPage
import com.tuozhu.consumablestatistics.ui.theme.BorderLight
import com.tuozhu.consumablestatistics.ui.theme.PageBg
import com.tuozhu.consumablestatistics.ui.theme.SlateBlue
import com.tuozhu.consumablestatistics.ui.theme.SlateMuted
import com.tuozhu.consumablestatistics.ui.theme.TextMuted
import com.tuozhu.consumablestatistics.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConsumableRootPage(
    val title: String,
    val subtitle: String,
) {
    OVERVIEW("总览", "今天先看活动卷、库存风险和待处理同步"),
    INVENTORY("卷库", "集中管理活动卷和其他耗材卷"),
    SYNC("同步", "处理桌面同步、扫码配对和待确认打印"),
    HISTORY("记录", "查看打印历史和最近事件"),
}

@Composable
fun ConsumableApp(viewModel: ConsumableViewModel) {
    ConsumableWorkspaceApp(viewModel = viewModel)
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
    var deleteJobId by rememberSaveable { mutableLongStateOf(-1L) }
    var shelfExpandedOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var archivedExpandedOverride by rememberSaveable { mutableStateOf(false) }
    var currentPage by rememberSaveable { mutableStateOf(ConsumableRootPage.OVERVIEW) }

    val shelfRolls = if (state.activeRoll == null) state.rolls else state.rolls.filterNot { it.id == state.activeRoll?.id }
    val allowShelfCollapse = shelfRolls.size > 2
    val shelfExpanded = if (allowShelfCollapse) shelfExpandedOverride ?: false else true
    val archivedExpanded = if (state.archivedRolls.size > 1) archivedExpandedOverride else true
    val showAddFab = currentPage == ConsumableRootPage.OVERVIEW || currentPage == ConsumableRootPage.INVENTORY

    val context = LocalContext.current
    val exportJson by viewModel.observeExportJson().collectAsStateWithLifecycle()
    val importPreview by viewModel.observeImportPreview().collectAsStateWithLifecycle()
    val isRefreshing by viewModel.observeIsRefreshing().collectAsStateWithLifecycle()
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null && pendingExportJson != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(pendingExportJson!!.toByteArray(Charsets.UTF_8)) }
                viewModel.clearExportData()
                pendingExportJson = null
                Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                if (json.isNullOrBlank()) Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
                else viewModel.prepareImport(json)
            } catch (e: Exception) {
                Toast.makeText(context, "读取失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(exportJson) {
        val json = exportJson ?: return@LaunchedEffect
        pendingExportJson = json
        createDocumentLauncher.launch("tuozhu-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())}.json")
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Box(modifier = Modifier.fillMaxSize().background(PageBg)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentPage.title, style = MaterialTheme.typography.titleMedium)
                            Text(currentPage.subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = PageBg,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            },
            floatingActionButton = {
                if (showAddFab) {
                    FloatingActionButton(
                        onClick = { addDialogVisible = true },
                        containerColor = SlateBlue,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                    ) { Icon(Icons.Outlined.Add, "新增") }
                }
            },
            bottomBar = {
                BottomTabBar(currentPage = currentPage, onSelectPage = { currentPage = it })
            },
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(280)) { width -> direction * width / 6 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(280)) { width -> -direction * width / 6 } + fadeOut(tween(200)))
                },
                label = "page",
            ) { page ->
                when (page) {
                    ConsumableRootPage.OVERVIEW -> OverviewPage(
                        state = state,
                        onOpenSync = { currentPage = ConsumableRootPage.SYNC },
                        onOpenHistory = { currentPage = ConsumableRootPage.HISTORY },
                        onConsumeClick = { consumeRollId = it },
                        onAdjustClick = { adjustRollId = it },
                        onDeleteClick = { deleteRollId = it },
                    )
                    ConsumableRootPage.INVENTORY -> InventoryPage(
                        state = state, shelfRolls = shelfRolls, shelfExpanded = shelfExpanded,
                        allowShelfCollapse = allowShelfCollapse, archivedExpanded = archivedExpanded,
                        onToggleShelfExpanded = { if (allowShelfCollapse) shelfExpandedOverride = !shelfExpanded },
                        onToggleArchivedExpanded = { if (state.archivedRolls.size > 1) archivedExpandedOverride = !archivedExpanded },
                        onConsumeClick = { consumeRollId = it }, onAdjustClick = { adjustRollId = it },
                        onDeleteClick = { deleteRollId = it }, onSetActiveClick = viewModel::setActiveRoll,
                        onExport = viewModel::prepareExport,
                        onImport = { openDocumentLauncher.launch(arrayOf("application/json")) },
                    )
                    ConsumableRootPage.SYNC -> SyncPage(
                        syncStatus = state.syncStatus, pendingJobs = state.pendingPrintJobs, activeRoll = state.activeRoll,
                        onSaveEndpoint = viewModel::saveDesktopSyncAddress, onPullSync = viewModel::pullSync,
                        onDiscoverLan = viewModel::autoDiscoverLanDesktop, onScanPairing = viewModel::pairWithScannedDesktopAddress,
                        onConfirmJob = viewModel::confirmPrintJob, onDeleteJob = { deleteJobId = it },
                        isRefreshing = isRefreshing, onRefresh = viewModel::pullSync,
                    )
                    ConsumableRootPage.HISTORY -> HistoryPage(state = state)
                }
            }
        }
    }

    // ── Dialogs ──
    if (addDialogVisible) {
        AddRollDialog(
            onDismiss = { addDialogVisible = false },
            onConfirm = { input -> viewModel.addRoll(input); addDialogVisible = false },
        )
    }

    state.rolls.firstOrNull { it.id == consumeRollId }?.let { roll ->
        RefinedWeightChangeDialog(
            title = "登记耗材", confirmLabel = "保存",
            description = "记录本次打印大约消耗的克重。", rollName = roll.displayName, initialValue = "",
            onDismiss = { consumeRollId = -1L },
            onConfirm = { grams, note -> viewModel.consumeRoll(roll.id, grams, note); consumeRollId = -1L },
        )
    }

    state.rolls.firstOrNull { it.id == adjustRollId }?.let { roll ->
        RefinedWeightChangeDialog(
            title = "校准余量", confirmLabel = "校准",
            description = "输入实际称到的剩余克重作为新基准。", rollName = roll.displayName,
            initialValue = roll.estimatedRemainingGrams.toString(), allowZero = true,
            onDismiss = { adjustRollId = -1L },
            onConfirm = { grams, note -> viewModel.recalibrateRoll(roll.id, grams, note); adjustRollId = -1L },
        )
    }

    state.rolls.firstOrNull { it.id == deleteRollId }?.let { roll ->
        ArchiveRollDialog(
            rollName = roll.displayName, isActive = roll.isActive,
            onDismiss = { deleteRollId = -1L },
            onConfirm = { viewModel.deleteRoll(roll.id); deleteRollId = -1L },
        )
    }

    importPreview?.let { preview ->
        ImportPreviewDialog(
            rollCount = preview.rollCount, printJobCount = preview.printJobCount,
            onDismiss = viewModel::cancelImport, onConfirm = viewModel::confirmImport,
        )
    }

    state.pendingPrintJobs.firstOrNull { it.id == deleteJobId }?.let { job ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteJobId = -1L },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("删除待确认任务", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("确认删除「${job.modelName}」吗？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("删除后该打印任务将永久移除，不可撤销。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePendingPrintJob(job.id); deleteJobId = -1L }) {
                    Text("确认删除", color = com.tuozhu.consumablestatistics.ui.theme.SignalRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleteJobId = -1L }) { Text("取消") } },
        )
    }
}

@Composable
private fun BottomTabBar(currentPage: ConsumableRootPage, onSelectPage: (ConsumableRootPage) -> Unit) {
    Surface(
        color = PageBg.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TabItem(Modifier.weight(1f), currentPage == ConsumableRootPage.OVERVIEW, "总览", { Icon(Icons.Outlined.Home, null) }) { onSelectPage(ConsumableRootPage.OVERVIEW) }
            TabItem(Modifier.weight(1f), currentPage == ConsumableRootPage.INVENTORY, "卷库", { Icon(Icons.Outlined.Inventory2, null) }) { onSelectPage(ConsumableRootPage.INVENTORY) }
            TabItem(Modifier.weight(1f), currentPage == ConsumableRootPage.SYNC, "同步", { Icon(Icons.Outlined.Sync, null) }) { onSelectPage(ConsumableRootPage.SYNC) }
            TabItem(Modifier.weight(1f), currentPage == ConsumableRootPage.HISTORY, "记录", { Icon(Icons.Outlined.History, null) }) { onSelectPage(ConsumableRootPage.HISTORY) }
        }
    }
}

@Composable
private fun TabItem(modifier: Modifier, selected: Boolean, label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    TextButton(
        modifier = modifier.heightIn(min = 48.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = if (selected) SlateMuted else Color.Transparent,
            contentColor = if (selected) SlateBlue else TextMuted,
        ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
