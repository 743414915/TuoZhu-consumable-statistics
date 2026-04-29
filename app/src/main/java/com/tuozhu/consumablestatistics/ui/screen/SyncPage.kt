package com.tuozhu.consumablestatistics.ui.screen

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.domain.SupportedMaterials
import com.tuozhu.consumablestatistics.sync.DesktopEndpointKind
import com.tuozhu.consumablestatistics.sync.connectionScopeHint
import com.tuozhu.consumablestatistics.sync.connectionScopeLabel
import com.tuozhu.consumablestatistics.sync.normalizeDesktopBaseUrl
import com.tuozhu.consumablestatistics.ui.PendingPrintJobUiModel
import com.tuozhu.consumablestatistics.ui.RollUiModel
import com.tuozhu.consumablestatistics.ui.SyncStatusUiModel
import com.tuozhu.consumablestatistics.ui.theme.ClayOrange
import com.tuozhu.consumablestatistics.ui.theme.MossInk
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SuccessMint

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SyncPage(
    syncStatus: SyncStatusUiModel,
    pendingJobs: List<PendingPrintJobUiModel>,
    activeRoll: RollUiModel?,
    modifier: Modifier = Modifier,
    onSaveEndpoint: (String) -> Unit,
    onPullSync: () -> Unit,
    onDiscoverLan: () -> Unit,
    onScanPairing: (String?) -> Unit,
    onConfirmJob: (Long) -> Unit,
    onDeleteJob: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    var deleteJobId by rememberSaveable { mutableLongStateOf(-1L) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SyncStatusCard(
                    syncStatus = syncStatus,
                    onSaveEndpoint = onSaveEndpoint,
                    onPullSync = onPullSync,
                    onDiscoverLan = onDiscoverLan,
                    onScanPairing = onScanPairing,
                )
            }
            if (pendingJobs.isNotEmpty()) {
                item {
                    PendingJobsCard(
                        jobs = pendingJobs,
                        activeRoll = activeRoll,
                        onConfirmJob = onConfirmJob,
                        onDeleteJob = { deleteJobId = it },
                    )
                }
            } else {
                item { EmptySyncHint() }
            }
        }
    }

    val deleteJob = pendingJobs.firstOrNull { it.id == deleteJobId }
    if (deleteJob != null) {
        AlertDialog(
            onDismissRequest = { deleteJobId = -1L },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("删除待确认任务", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("确认删除「${deleteJob.modelName}」吗？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("删除后该打印任务将永久移除，不会扣减任何耗材卷。这个操作不可撤销。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteJob(deleteJob.id)
                    deleteJobId = -1L
                }) { Text("确认删除", color = Color(0xFFB43A2E)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteJobId = -1L }) { Text("取消") }
            },
        )
    }
}

// ── Sync Status ──

@Composable
private fun SyncStatusCard(
    syncStatus: SyncStatusUiModel,
    onSaveEndpoint: (String) -> Unit,
    onPullSync: () -> Unit,
    onDiscoverLan: () -> Unit,
    onScanPairing: (String?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var expanded by rememberSaveable { mutableStateOf(!syncStatus.isConfigured) }
    var endpointInput by rememberSaveable { mutableStateOf(syncStatus.desktopBaseUrl) }
    val normalizedInput = normalizeDesktopBaseUrl(endpointInput)
    val hasPendingChanges = normalizedInput != syncStatus.desktopBaseUrl
    val syncBusy = syncStatus.isPulling || syncStatus.isDiscoveringLan

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) Toast.makeText(context, "已取消扫码", Toast.LENGTH_SHORT).show()
        else onScanPairing(raw)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanLauncher.launch(buildQrScanOptions())
        else {
            val blocked = activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            if (blocked) {
                Toast.makeText(context, "相机权限已被禁止，请在系统设置中开启后再扫码", Toast.LENGTH_LONG).show()
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
            } else Toast.makeText(context, "未授予相机权限，请改用手动填写地址", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(syncStatus.desktopBaseUrl) {
        endpointInput = syncStatus.desktopBaseUrl
        if (!syncStatus.isConfigured) expanded = true
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("桌面同步", style = MaterialTheme.typography.titleLarge)
                    Text("来源 ${syncStatus.source.toUiLabel()} · ${syncStatus.lastSyncLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val (bg, fg) = when (syncStatus.status) {
                    SyncConnectionStatus.IDLE -> ClayOrange.copy(alpha = 0.14f) to ClayOrange
                    SyncConnectionStatus.SUCCESS -> SuccessMint.copy(alpha = 0.14f) to MossInk
                    SyncConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                    SyncConnectionStatus.ERROR -> SignalRed.copy(alpha = 0.12f) to SignalRed
                }
                Pill(syncStatus.status.toUiLabel(), bg, fg) { Icon(Icons.Outlined.Sync, null, Modifier.size(14.dp)) }
            }
            Text(syncStatus.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("当前已绑定的桌面地址", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Computer, null, tint = ClayOrange, modifier = Modifier.size(18.dp))
                        Text(if (syncStatus.isConfigured) syncStatus.desktopBaseUrl else "正在检测或尚未设置桌面同步地址", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("地址类型 ${syncStatus.addressKindLabel}",
                            when (syncStatus.addressKind) {
                                DesktopEndpointKind.TAILSCALE, DesktopEndpointKind.MAGIC_DNS -> ClayOrange.copy(alpha = 0.12f)
                                DesktopEndpointKind.LAN -> MaterialTheme.colorScheme.secondaryContainer
                                DesktopEndpointKind.CUSTOM -> MaterialTheme.colorScheme.tertiaryContainer
                                DesktopEndpointKind.NONE -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            when (syncStatus.addressKind) {
                                DesktopEndpointKind.TAILSCALE, DesktopEndpointKind.MAGIC_DNS -> ClayOrange
                                DesktopEndpointKind.LAN -> MaterialTheme.colorScheme.onSecondaryContainer
                                DesktopEndpointKind.CUSTOM -> MaterialTheme.colorScheme.onTertiaryContainer
                                DesktopEndpointKind.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ) {
                            Icon(when (syncStatus.addressKind) {
                                DesktopEndpointKind.TAILSCALE, DesktopEndpointKind.MAGIC_DNS -> Icons.Outlined.Public
                                DesktopEndpointKind.LAN -> Icons.Outlined.Lan
                                else -> Icons.Outlined.Computer
                            }, null, Modifier.size(14.dp))
                        }
                        Pill(syncStatus.addressKind.connectionScopeLabel(),
                            if (syncStatus.addressKind == DesktopEndpointKind.TAILSCALE || syncStatus.addressKind == DesktopEndpointKind.MAGIC_DNS) SuccessMint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            if (syncStatus.addressKind == DesktopEndpointKind.TAILSCALE || syncStatus.addressKind == DesktopEndpointKind.MAGIC_DNS) MossInk else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(syncStatus.addressKind.connectionScopeHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Pill("待确认打印任务 ${syncStatus.pendingCount} 条",
                    if (syncStatus.pendingCount > 0) ClayOrange.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                    if (syncStatus.pendingCount > 0) ClayOrange else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { if (hasPendingChanges || !syncStatus.isConfigured) expanded = true else onPullSync() },
                modifier = Modifier.fillMaxWidth(), enabled = !syncBusy,
                colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text(when { syncStatus.isPulling -> "正在拉取桌面记录..." ; hasPendingChanges -> "先保存地址再拉取" ; syncStatus.isConfigured -> "立即拉取桌面打印记录" ; else -> "先填写桌面地址" })
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    if (activity == null) { Toast.makeText(context, "当前页面无法启动扫码器", Toast.LENGTH_SHORT).show(); return@OutlinedButton }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanLauncher.launch(buildQrScanOptions())
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }, modifier = Modifier.weight(1f), enabled = !syncBusy) { Text("扫码配对") }
                OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, ClayOrange.copy(alpha = 0.35f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = ClayOrange)) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) "收起地址设置" else if (syncStatus.isConfigured) "编辑地址设置" else "填写桌面地址")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("支持直接输入 Tailscale IP、MagicDNS 域名，或局域网 IP。未填写协议时会自动补成 http://", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = endpointInput, onValueChange = { endpointInput = it }, label = { Text("桌面同步地址") }, placeholder = { Text("例如 100.x.x.x:8823、电脑名.tailnet.ts.net:8823 或 192.168.x.x:8823") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !syncBusy)
                    if (hasPendingChanges) Text("地址有未保存改动。立即拉取仍会使用上一次已保存的地址。", style = MaterialTheme.typography.bodySmall, color = ClayOrange)
                    Text(if (syncStatus.isDiscoveringLan) "正在扫描同一 Wi‑Fi 下的桌面同步服务，通常需要几秒钟。" else "如果你已经接入 Tailscale，请优先使用桌面端主推荐地址或二维码配对。", style = MaterialTheme.typography.bodySmall, color = if (syncStatus.isDiscoveringLan) ClayOrange else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = { onSaveEndpoint(endpointInput); if (normalizedInput.isNotBlank()) expanded = false }, enabled = !syncBusy, colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White)) { Text(if (normalizedInput.isBlank()) "清空地址" else "保存地址") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = onDiscoverLan, enabled = !syncBusy) { Text(if (syncStatus.isDiscoveringLan) "扫描同一 Wi‑Fi..." else "扫描同一 Wi‑Fi") }
                    }
                }
            }
        }
    }
}

// ── Pending Jobs ──

@Composable
private fun PendingJobsCard(
    jobs: List<PendingPrintJobUiModel>,
    activeRoll: RollUiModel?,
    onConfirmJob: (Long) -> Unit,
    onDeleteJob: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("待确认打印任务", style = MaterialTheme.typography.titleLarge)
            Text(
                if (activeRoll != null) "确认后会记入当前活动卷：${activeRoll.displayName}" else "请先设置活动卷，再确认自动同步任务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            jobs.forEachIndexed { i, job ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDeleteJob(job.id); false } else false }),
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().background(SignalRed.copy(alpha = 0.15f), RoundedCornerShape(28.dp)).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                            Icon(Icons.Outlined.Delete, "删除", tint = SignalRed)
                        }
                    },
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                ) { JobCard(job, activeRoll, { onConfirmJob(job.id) }, { onDeleteJob(job.id) }) }
            }
        }
    }
}

@Composable
private fun JobCard(job: PendingPrintJobUiModel, activeRoll: RollUiModel?, onConfirm: () -> Unit, onDelete: () -> Unit) {
    val required = SupportedMaterials.normalize(job.targetMaterial)
    val activeMat = SupportedMaterials.normalize(activeRoll?.material)
    val materialOk = required == null || activeMat == required
    val hint = when {
        job.isConfirming -> "正在提交确认，请稍候"
        activeRoll == null -> "尚未选择活动卷"
        !materialOk -> "当前活动卷为 ${activeRoll.material}，请先切换到 $required"
        else -> "将作用于 ${activeRoll.displayName}"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(job.modelName, style = MaterialTheme.typography.titleMedium)
                Text("${job.sourceLabel} | ${job.createdAtLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill("${job.estimatedUsageGrams}g", ClayOrange.copy(alpha = 0.14f), ClayOrange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            job.targetMaterial?.let { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            if (!materialOk && activeRoll != null) Pill("材料不匹配", SignalRed.copy(alpha = 0.12f), SignalRed)
        }
        Text(job.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = activeRoll != null && materialOk && !job.isConfirming, onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = ClayOrange, contentColor = Color.White)) { Text("确认并扣减") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(), onClick = onDelete, enabled = !job.isConfirming,
            border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = SignalRed.copy(alpha = 0.05f), contentColor = SignalRed),
        ) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(8.dp)); Text("删除此任务") }
    }
}

// ── Empty ──

@Composable
private fun EmptySyncHint() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("当前没有待确认打印任务", style = MaterialTheme.typography.titleLarge)
            Text("同步页现在只保留桌面连接和打印确认相关操作。若桌面端已切片完成，点击“立即拉取”即可刷新。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Shared utilities ──

@Composable
private fun Pill(text: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color, icon: @Composable (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(14.dp), color = containerColor) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

private fun buildQrScanOptions() = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("请扫描桌面端配对二维码")
    setBeepEnabled(false)
    setOrientationLocked(true)
}

private fun SyncConnectionStatus.toUiLabel() = when (this) {
    SyncConnectionStatus.IDLE -> "等待同步"
    SyncConnectionStatus.SUCCESS -> "同步成功"
    SyncConnectionStatus.OFFLINE -> "桌面离线"
    SyncConnectionStatus.ERROR -> "同步失败"
}

private fun com.tuozhu.consumablestatistics.data.SyncSourceType.toUiLabel() = when (this) {
    com.tuozhu.consumablestatistics.data.SyncSourceType.MANUAL -> "手动"
    com.tuozhu.consumablestatistics.data.SyncSourceType.DESKTOP_AGENT -> "桌面同步"
    com.tuozhu.consumablestatistics.data.SyncSourceType.CLOUD -> "云同步"
}
