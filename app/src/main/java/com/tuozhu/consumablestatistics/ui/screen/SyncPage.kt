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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.tuozhu.consumablestatistics.ui.theme.BorderLight
import com.tuozhu.consumablestatistics.ui.theme.RedMuted
import com.tuozhu.consumablestatistics.ui.theme.SignalRed
import com.tuozhu.consumablestatistics.ui.theme.SlateBlue
import com.tuozhu.consumablestatistics.ui.theme.SlateBlueDark
import com.tuozhu.consumablestatistics.ui.theme.SlateMuted
import com.tuozhu.consumablestatistics.ui.theme.SuccessGreen
import com.tuozhu.consumablestatistics.ui.theme.TextMuted
import com.tuozhu.consumablestatistics.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
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

    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SyncStatusCard(syncStatus, onSaveEndpoint, onPullSync, onDiscoverLan, onScanPairing) }
            if (pendingJobs.isNotEmpty()) {
                item { PendingJobsCard(pendingJobs, activeRoll, onConfirmJob, { deleteJobId = it }) }
            } else {
                item { EmptySyncHint() }
            }
        }
    }

    val job = pendingJobs.firstOrNull { it.id == deleteJobId }
    if (job != null) {
        AlertDialog(
            onDismissRequest = { deleteJobId = -1L },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("删除待确认任务", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("确认删除「${job.modelName}」吗？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("删除后不可撤销，不会扣减任何耗材卷。", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onDeleteJob(job.id); deleteJobId = -1L }) {
                    Text("确认删除", color = SignalRed)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteJobId = -1L }) { Text("取消") }
            },
        )
    }
}

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
    val syncBusy = syncStatus.isPulling || syncStatus.isDiscoveringLan

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) Toast.makeText(context, "已取消扫码", Toast.LENGTH_SHORT).show()
        else onScanPairing(raw)
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanLauncher.launch(buildQrScanOptions())
        else {
            val blocked = activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            Toast.makeText(context, if (blocked) "相机权限已被禁止" else "未授予相机权限", Toast.LENGTH_LONG).show()
            if (blocked) context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
        }
    }

    LaunchedEffect(syncStatus.desktopBaseUrl) {
        endpointInput = syncStatus.desktopBaseUrl
        if (!syncStatus.isConfigured) expanded = true
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("桌面同步", style = MaterialTheme.typography.titleMedium)
                    Text("来源 ${syncStatus.source.toUiLabel()} · ${syncStatus.lastSyncLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                val (bg, fg) = when (syncStatus.status) {
                    SyncConnectionStatus.IDLE -> SlateMuted to SlateBlue
                    SyncConnectionStatus.SUCCESS -> Color(0xFFECFDF5) to SuccessGreen
                    SyncConnectionStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant to TextSecondary
                    SyncConnectionStatus.ERROR -> RedMuted to SignalRed
                }
                Pill(syncStatus.status.toUiLabel(), bg, fg) { Icon(Icons.Outlined.Sync, null, Modifier.size(14.dp)) }
            }
            Text(syncStatus.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已绑定的桌面地址", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Computer, null, tint = SlateBlue, modifier = Modifier.size(18.dp))
                        Text(if (syncStatus.isConfigured) syncStatus.desktopBaseUrl else "尚未设置", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (kindBg, kindFg) = when (syncStatus.addressKind) {
                            DesktopEndpointKind.TAILSCALE, DesktopEndpointKind.MAGIC_DNS -> SlateMuted to SlateBlue
                            DesktopEndpointKind.LAN -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant to TextSecondary
                        }
                        Pill("地址类型 ${syncStatus.addressKindLabel}", kindBg, kindFg) {
                            Icon(when (syncStatus.addressKind) {
                                DesktopEndpointKind.TAILSCALE, DesktopEndpointKind.MAGIC_DNS -> Icons.Outlined.Public
                                DesktopEndpointKind.LAN -> Icons.Outlined.Lan
                                else -> Icons.Outlined.Computer
                            }, null, Modifier.size(14.dp))
                        }
                        Pill(syncStatus.addressKind.connectionScopeLabel(), MaterialTheme.colorScheme.surfaceVariant, TextSecondary)
                    }
                    Text(syncStatus.addressKind.connectionScopeHint(), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }

            Button(
                onClick = { if (!syncStatus.isConfigured) expanded = true else onPullSync() },
                modifier = Modifier.fillMaxWidth(), enabled = !syncBusy,
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text(if (syncStatus.isPulling) "正在拉取..." else if (!syncStatus.isConfigured) "先填写桌面地址" else "立即拉取桌面打印记录")
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (activity == null) { Toast.makeText(context, "无法启动扫码器", Toast.LENGTH_SHORT).show(); return@OutlinedButton }
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanLauncher.launch(buildQrScanOptions())
                        else camPerm.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f), enabled = !syncBusy,
                    shape = RoundedCornerShape(14.dp),
                ) { Text("扫码配对") }
                OutlinedButton(
                    onClick = { expanded = !expanded }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SlateBlue.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateBlue),
                ) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "收起" else "编辑地址")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = endpointInput, onValueChange = { endpointInput = it },
                        label = { Text("桌面同步地址") },
                        placeholder = { Text("100.x.x.x:8823 或 192.168.x.x:8823") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !syncBusy,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            modifier = Modifier.weight(1f), onClick = { onSaveEndpoint(endpointInput); if (normalizedInput.isNotBlank()) expanded = false },
                            enabled = !syncBusy, colors = ButtonDefaults.buttonColors(containerColor = SlateBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(if (normalizedInput.isBlank()) "清空地址" else "保存地址") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f), onClick = onDiscoverLan, enabled = !syncBusy,
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(if (syncStatus.isDiscoveringLan) "扫描中..." else "扫描同一 Wi‑Fi") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingJobsCard(
    jobs: List<PendingPrintJobUiModel>,
    activeRoll: RollUiModel?,
    onConfirm: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("待确认打印任务", style = MaterialTheme.typography.titleMedium)
            Text(
                if (activeRoll != null) "确认后记入活动卷：${activeRoll.displayName}" else "请先设置活动卷再确认",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
            )
            jobs.forEachIndexed { i, job ->
                if (i > 0) HorizontalDivider(color = BorderLight)
                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(job.id); false } else false }),
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize()
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, SignalRed.copy(alpha = 0.92f))), RoundedCornerShape(20.dp))
                                .padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Delete, "删除", tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("确认删除", color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    },
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                ) {
                    JobCard(job, activeRoll, { onConfirm(job.id) }, { onDelete(job.id) })
                }
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
        job.isConfirming -> "正在提交确认..."
        activeRoll == null -> "尚未选择活动卷"
        !materialOk -> "当前活动卷为 ${activeRoll?.material}，请切换到 $required"
        else -> "将作用于 ${activeRoll?.displayName}"
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(job.modelName, style = MaterialTheme.typography.titleMedium)
                    Text("${job.sourceLabel} · ${job.createdAtLabel}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Pill("${job.estimatedUsageGrams}g", SlateMuted, SlateBlue)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                job.targetMaterial?.let { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
                if (!materialOk && activeRoll != null) Pill("材料不匹配", RedMuted, SignalRed)
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = if (!materialOk) SignalRed else TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(1.dp))
                Button(
                    enabled = activeRoll != null && materialOk && !job.isConfirming,
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("确认并扣减") }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(), onClick = onDelete, enabled = !job.isConfirming,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SignalRed.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed),
            ) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("删除此任务") }
        }
    }
}

@Composable
private fun EmptySyncHint() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight),
    ) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("当前没有待确认打印任务", style = MaterialTheme.typography.titleMedium)
            Text("点击「立即拉取」刷新桌面同步记录。", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun Pill(text: String, containerColor: Color, contentColor: Color, icon: @Composable (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(12.dp), color = containerColor) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
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
