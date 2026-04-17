package com.tuozhu.consumablestatistics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tuozhu.consumablestatistics.data.FilamentEventEntity
import com.tuozhu.consumablestatistics.data.FilamentRepository
import com.tuozhu.consumablestatistics.data.FilamentRollEntity
import com.tuozhu.consumablestatistics.data.PrintJobEntity
import com.tuozhu.consumablestatistics.data.PrintJobStatus
import com.tuozhu.consumablestatistics.data.RollSnapshot
import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.data.SyncSourceType
import com.tuozhu.consumablestatistics.data.SyncStateEntity
import com.tuozhu.consumablestatistics.data.isArchivedRoll
import com.tuozhu.consumablestatistics.domain.SupportedMaterials
import com.tuozhu.consumablestatistics.domain.WeightMath
import com.tuozhu.consumablestatistics.sync.DesktopEndpointKind
import com.tuozhu.consumablestatistics.sync.DesktopLanDiscoveryResult
import com.tuozhu.consumablestatistics.sync.DesktopLanEndpointDiscovery
import com.tuozhu.consumablestatistics.sync.SyncSettings
import com.tuozhu.consumablestatistics.sync.SyncSettingsStore
import com.tuozhu.consumablestatistics.sync.classifyDesktopBaseUrl
import com.tuozhu.consumablestatistics.sync.normalizeScannedDesktopBaseUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class RollInput(
    val brand: String,
    val name: String,
    val material: String,
    val colorName: String,
    val colorHex: String,
    val initialWeightGrams: Int,
    val remainingWeightGrams: Int,
    val lowStockThresholdGrams: Int,
    val notes: String,
)

data class RollUiModel(
    val id: Long,
    val brand: String,
    val displayName: String,
    val material: String,
    val colorHex: String,
    val initialWeightGrams: Int,
    val estimatedRemainingGrams: Int,
    val progress: Float,
    val isLowStock: Boolean,
    val isActive: Boolean,
    val notes: String,
    val calibrationLabel: String,
)

data class FilamentEventItem(
    val id: Long,
    val rollLabel: String,
    val type: String,
    val source: SyncSourceType,
    val deltaGrams: Int,
    val note: String,
    val createdAt: Long,
    val remainingAfterGrams: Int,
)

data class RecentEventUiModel(
    val id: Long,
    val title: String,
    val note: String,
    val timestampLabel: String,
)

data class SyncStatusUiModel(
    val status: SyncConnectionStatus = SyncConnectionStatus.IDLE,
    val source: SyncSourceType = SyncSourceType.MANUAL,
    val lastSyncLabel: String = "尚未同步",
    val message: String = "桌面自动同步尚未连接",
    val pendingCount: Int = 0,
    val desktopBaseUrl: String = "",
    val isConfigured: Boolean = false,
    val addressKind: DesktopEndpointKind = DesktopEndpointKind.NONE,
    val addressKindLabel: String = "未设置",
    val isDiscoveringLan: Boolean = false,
    val isPulling: Boolean = false,
)

data class PendingPrintJobUiModel(
    val id: Long,
    val modelName: String,
    val sourceLabel: String,
    val estimatedUsageGrams: Int,
    val targetMaterial: String?,
    val note: String,
    val createdAtLabel: String,
    val isConfirming: Boolean,
)

data class PrintHistoryJobUiModel(
    val id: Long,
    val modelName: String,
    val sourceLabel: String,
    val estimatedUsageGrams: Int,
    val targetMaterial: String?,
    val note: String,
    val createdAtLabel: String,
    val confirmedAtLabel: String,
    val rollLabel: String?,
    val rollArchived: Boolean,
)

data class PrintTaskTimelineUiModel(
    val id: Long,
    val modelName: String,
    val status: PrintJobStatus,
    val statusLabel: String,
    val sourceLabel: String,
    val estimatedUsageGrams: Int,
    val targetMaterial: String?,
    val note: String,
    val createdAtLabel: String,
    val updatedAtLabel: String,
    val updatedAtPrefix: String,
    val rollLabel: String?,
    val rollArchived: Boolean,
    val externalJobId: String,
)

data class PrintTaskHistorySummaryUiModel(
    val pendingCount: Int = 0,
    val confirmedCount: Int = 0,
    val confirmedUsageGrams: Int = 0,
)

data class ConsumableUiState(
    val rolls: List<RollUiModel> = emptyList(),
    val activeRoll: RollUiModel? = null,
    val recentEvents: List<RecentEventUiModel> = emptyList(),
    val syncStatus: SyncStatusUiModel = SyncStatusUiModel(),
    val pendingPrintJobs: List<PendingPrintJobUiModel> = emptyList(),
    val printHistory: List<PrintHistoryJobUiModel> = emptyList(),
    val recentPrintTasks: List<PrintTaskTimelineUiModel> = emptyList(),
    val printTaskSummary: PrintTaskHistorySummaryUiModel = PrintTaskHistorySummaryUiModel(),
    val message: String? = null,
)

class ConsumableViewModel(
    private val repository: FilamentRepository,
    private val syncSettingsStore: SyncSettingsStore,
    private val lanEndpointDiscovery: DesktopLanEndpointDiscovery = DesktopLanEndpointDiscovery(),
) : ViewModel() {
    private val messageState = MutableStateFlow<String?>(null)
    private val confirmingJobIdsState = MutableStateFlow<Set<Long>>(emptySet())
    private val isDiscoveringLanState = MutableStateFlow(false)
    private val isPullingState = MutableStateFlow(false)
    private val pullSyncMutex = Mutex()
    private val rollsFlow = repository.observeRolls().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val recentEventsFlow = repository.observeRecentEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val pendingJobsFlow = repository.observePendingPrintJobs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val printHistoryFlow = repository.observePrintHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val recentPrintJobsFlow = repository.observeRecentPrintJobs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val syncStateFlow = repository.observeSyncState().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
    private val syncSettingsFlow = syncSettingsStore.observeSettings().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = syncSettingsStore.currentSettings(),
    )

    init {
        if (syncSettingsStore.currentSettings().desktopBaseUrl.isNotBlank()) {
            pullSync()
        }
    }

    private val combinedDataFlow = combine(
        rollsFlow,
        recentEventsFlow,
        pendingJobsFlow,
        printHistoryFlow,
        syncStateFlow,
    ) { rolls, recentEvents, pendingJobs, printHistory, syncState ->
        CombinedCoreUiData(
            rolls = rolls,
            recentEvents = recentEvents,
            pendingJobs = pendingJobs,
            printHistory = printHistory,
            syncState = syncState,
        )
    }
        .combine(recentPrintJobsFlow) { data, recentPrintJobs ->
            data.copy(recentPrintJobs = recentPrintJobs)
        }
        .combine(syncSettingsFlow) { data, syncSettings ->
            CombinedUiData(
                rolls = data.rolls,
                recentEvents = data.recentEvents,
                pendingJobs = data.pendingJobs,
                printHistory = data.printHistory,
                recentPrintJobs = data.recentPrintJobs,
                syncState = data.syncState,
                syncSettings = syncSettings,
                isDiscoveringLan = false,
            )
        }
        .combine(isDiscoveringLanState) { data, isDiscoveringLan ->
            data.copy(isDiscoveringLan = isDiscoveringLan)
        }
        .combine(isPullingState) { data, isPulling ->
            data.copy(isPulling = isPulling)
        }

    val uiState: StateFlow<ConsumableUiState> = combine(
        combinedDataFlow,
        messageState,
        confirmingJobIdsState,
    ) { data, message, confirmingJobIds ->
        val snapshots = data.rolls.map { roll ->
            RollSnapshot(
                roll = roll,
                estimatedRemainingGrams = repository.calculateRemaining(roll),
            )
        }
        val visibleRollItems = snapshots
            .filterNot { it.roll.isArchivedRoll() }
            .map(::toRollUiModel)
        ConsumableUiState(
            rolls = visibleRollItems,
            activeRoll = visibleRollItems.firstOrNull { it.isActive },
            recentEvents = mapRecentEvents(data.rolls, data.recentEvents)
                .filterNot { it.type == "PRINT_USAGE" }
                .map(FilamentEventItem::toUiModel),
            syncStatus = toSyncStatusUi(
                syncState = data.syncState,
                pendingJobs = data.pendingJobs,
                syncSettings = data.syncSettings,
                isDiscoveringLan = data.isDiscoveringLan,
                isPulling = data.isPulling,
            ),
            pendingPrintJobs = data.pendingJobs.map { job ->
                job.toUiModel(confirmingJobIds.contains(job.id))
            },
            printHistory = data.printHistory.map { job ->
                job.toHistoryUiModel(data.rolls.firstOrNull { it.id == job.rollId })
            },
            recentPrintTasks = data.recentPrintJobs.map { job ->
                job.toPrintTaskTimelineUiModel(data.rolls.firstOrNull { it.id == job.rollId })
            },
            printTaskSummary = PrintTaskHistorySummaryUiModel(
                pendingCount = data.recentPrintJobs.count { it.status == PrintJobStatus.DRAFT },
                confirmedCount = data.recentPrintJobs.count { it.status == PrintJobStatus.CONFIRMED },
                confirmedUsageGrams = data.recentPrintJobs
                    .filter { it.status == PrintJobStatus.CONFIRMED }
                    .sumOf { it.estimatedUsageGrams },
            ),
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConsumableUiState(),
    )

    fun addRoll(input: RollInput) {
        viewModelScope.launch {
            repository.addRoll(
                brand = input.brand.ifBlank { "拓竹 Bambu Lab" },
                name = input.name.ifBlank { "未命名耗材" },
                material = input.material.ifBlank { SupportedMaterials.default },
                colorName = input.colorName.ifBlank { "未命名颜色" },
                colorHex = input.colorHex,
                initialWeightGrams = input.initialWeightGrams.coerceAtLeast(1),
                remainingWeightGrams = input.remainingWeightGrams.coerceAtLeast(0),
                lowStockThresholdGrams = input.lowStockThresholdGrams.coerceAtLeast(0),
                notes = input.notes,
            )
            messageState.value = "耗材卷已保存"
        }
    }

    fun consumeRoll(rollId: Long, grams: Int, note: String) {
        viewModelScope.launch {
            repository.consumeRoll(rollId, grams, note)
            messageState.value = "耗材记录已更新"
        }
    }

    fun recalibrateRoll(rollId: Long, newRemainingGrams: Int, note: String) {
        viewModelScope.launch {
            repository.recalibrateRoll(rollId, newRemainingGrams, note)
            messageState.value = "余量已校准"
        }
    }

    fun setActiveRoll(rollId: Long) {
        viewModelScope.launch {
            repository.setActiveRoll(rollId)
            messageState.value = "已切换当前活动卷"
        }
    }

    fun deleteRoll(rollId: Long) {
        viewModelScope.launch {
            val result = repository.deleteRoll(rollId)
            messageState.value = result.message
        }
    }

    fun saveDesktopSyncAddress(baseUrl: String) {
        syncSettingsStore.updateDesktopBaseUrl(baseUrl)
        messageState.value = if (syncSettingsStore.currentSettings().desktopBaseUrl.isBlank()) {
            "已清空桌面同步地址"
        } else {
            "桌面同步地址已保存"
        }
    }

    fun pullSync() {
        viewModelScope.launch {
            val result = performPullSync() ?: return@launch
            messageState.value = result.message
        }
    }

    fun autoDiscoverLanDesktop() {
        viewModelScope.launch {
            isDiscoveringLanState.value = true
            try {
                when (val result = lanEndpointDiscovery.discover()) {
                    is DesktopLanDiscoveryResult.Success -> {
                        syncSettingsStore.updateDesktopBaseUrl(result.baseUrl)
                        val pullResult = performPullSync()
                        messageState.value = if (pullResult == null) {
                            "已自动发现局域网地址 ${result.baseUrl}，正在刷新桌面记录"
                        } else {
                            "已自动发现局域网地址 ${result.baseUrl}。${pullResult.message}"
                        }
                    }

                    is DesktopLanDiscoveryResult.NotFound -> {
                        messageState.value = result.message
                    }

                    is DesktopLanDiscoveryResult.Unsupported -> {
                        messageState.value = result.message
                    }
                }
            } catch (exception: Exception) {
                messageState.value = "自动发现失败：${exception.message ?: "请稍后重试"}"
            } finally {
                isDiscoveringLanState.value = false
            }
        }
    }

    fun pairWithScannedDesktopAddress(rawValue: String?) {
        val normalized = normalizeScannedDesktopBaseUrl(rawValue)
        if (normalized == null) {
            messageState.value = "无法识别二维码里的桌面地址，请重新扫描桌面端二维码"
            return
        }
        syncSettingsStore.updateDesktopBaseUrl(normalized)
        viewModelScope.launch {
            val result = performPullSync()
            messageState.value = if (result == null) {
                "已通过二维码写入桌面地址，正在刷新桌面记录"
            } else {
                "已通过二维码写入桌面地址。${result.message}"
            }
        }
    }

    fun confirmPrintJob(jobId: Long) {
        if (confirmingJobIdsState.value.contains(jobId)) {
            return
        }
        viewModelScope.launch {
            confirmingJobIdsState.value = confirmingJobIdsState.value + jobId
            try {
                val result = repository.confirmPrintJob(jobId)
                messageState.value = result.message
            } finally {
                confirmingJobIdsState.value = confirmingJobIdsState.value - jobId
            }
        }
    }

    fun consumeMessage() {
        messageState.value = null
    }

    private fun toRollUiModel(snapshot: RollSnapshot): RollUiModel {
        val roll = snapshot.roll
        return RollUiModel(
            id = roll.id,
            brand = roll.brand,
            displayName = roll.displayLabel(),
            material = roll.material,
            colorHex = roll.colorHex,
            initialWeightGrams = roll.initialWeightGrams,
            estimatedRemainingGrams = snapshot.estimatedRemainingGrams,
            progress = WeightMath.progress(snapshot.estimatedRemainingGrams, roll.initialWeightGrams),
            isLowStock = WeightMath.isLowStock(snapshot.estimatedRemainingGrams, roll.lowStockThresholdGrams),
            isActive = roll.isActive,
            notes = roll.visibleNotes(),
            calibrationLabel = "上次校准 ${formatTimestamp(roll.lastCalibrationAt)}",
        )
    }

    private fun mapRecentEvents(
        rolls: List<FilamentRollEntity>,
        events: List<FilamentEventEntity>,
    ): List<FilamentEventItem> {
        val rollMap = rolls.associateBy { it.id }
        return events.mapNotNull { event ->
            val roll = rollMap[event.rollId] ?: return@mapNotNull null
            FilamentEventItem(
                id = event.id,
                rollLabel = roll.displayLabel(includeArchivedTag = true),
                type = event.type.name,
                source = event.source,
                deltaGrams = event.deltaGrams,
                note = event.note,
                createdAt = event.createdAt,
                remainingAfterGrams = event.remainingAfterGrams,
            )
        }
    }

    private fun toSyncStatusUi(
        syncState: SyncStateEntity?,
        pendingJobs: List<PrintJobEntity>,
        syncSettings: SyncSettings,
        isDiscoveringLan: Boolean,
        isPulling: Boolean,
    ): SyncStatusUiModel {
        val addressKind = classifyDesktopBaseUrl(syncSettings.desktopBaseUrl)
        return SyncStatusUiModel(
            status = syncState?.status ?: SyncConnectionStatus.IDLE,
            source = syncState?.lastSyncSource ?: SyncSourceType.MANUAL,
            lastSyncLabel = formatRelativeTime(syncState?.lastSyncAt),
            message = syncState?.lastMessage ?: "桌面自动同步尚未连接",
            pendingCount = pendingJobs.count { it.status == PrintJobStatus.DRAFT },
            desktopBaseUrl = syncSettings.desktopBaseUrl,
            isConfigured = syncSettings.desktopBaseUrl.isNotBlank(),
            addressKind = addressKind,
            addressKindLabel = addressKind.toUiLabel(),
            isDiscoveringLan = isDiscoveringLan,
            isPulling = isPulling,
        )
    }

    private suspend fun performPullSync(): com.tuozhu.consumablestatistics.sync.SyncPullResult? {
        if (!pullSyncMutex.tryLock()) {
            messageState.value = "正在拉取桌面打印记录，请稍候"
            return null
        }
        isPullingState.value = true
        return try {
            repository.pullSync()
        } finally {
            isPullingState.value = false
            pullSyncMutex.unlock()
        }
    }
}

private data class CombinedUiData(
    val rolls: List<FilamentRollEntity>,
    val recentEvents: List<FilamentEventEntity>,
    val pendingJobs: List<PrintJobEntity>,
    val printHistory: List<PrintJobEntity>,
    val recentPrintJobs: List<PrintJobEntity>,
    val syncState: SyncStateEntity?,
    val syncSettings: SyncSettings,
    val isDiscoveringLan: Boolean,
    val isPulling: Boolean = false,
)

private data class CombinedCoreUiData(
    val rolls: List<FilamentRollEntity>,
    val recentEvents: List<FilamentEventEntity>,
    val pendingJobs: List<PrintJobEntity>,
    val printHistory: List<PrintJobEntity>,
    val recentPrintJobs: List<PrintJobEntity> = emptyList(),
    val syncState: SyncStateEntity?,
)

class ConsumableViewModelFactory(
    private val repository: FilamentRepository,
    private val syncSettingsStore: SyncSettingsStore,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsumableViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConsumableViewModel(repository, syncSettingsStore) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}

private fun PrintJobEntity.toUiModel(isConfirming: Boolean): PendingPrintJobUiModel {
    return PendingPrintJobUiModel(
        id = id,
        modelName = modelName,
        sourceLabel = source.toUiLabel(),
        estimatedUsageGrams = estimatedUsageGrams,
        targetMaterial = targetMaterial,
        note = note,
        createdAtLabel = formatTimestamp(createdAt),
        isConfirming = isConfirming,
    )
}

private fun PrintJobEntity.toHistoryUiModel(roll: FilamentRollEntity?): PrintHistoryJobUiModel {
    return PrintHistoryJobUiModel(
        id = id,
        modelName = modelName,
        sourceLabel = source.toUiLabel(),
        estimatedUsageGrams = estimatedUsageGrams,
        targetMaterial = targetMaterial,
        note = note,
        createdAtLabel = formatTimestamp(createdAt),
        confirmedAtLabel = formatTimestamp(confirmedAt ?: createdAt),
        rollLabel = roll?.displayLabel(includeArchivedTag = true),
        rollArchived = roll?.isArchivedRoll() == true,
    )
}

private fun PrintJobEntity.toPrintTaskTimelineUiModel(roll: FilamentRollEntity?): PrintTaskTimelineUiModel {
    val effectiveTimestamp = confirmedAt ?: createdAt
    return PrintTaskTimelineUiModel(
        id = id,
        modelName = modelName,
        status = status,
        statusLabel = status.toUiLabel(),
        sourceLabel = source.toUiLabel(),
        estimatedUsageGrams = estimatedUsageGrams,
        targetMaterial = targetMaterial,
        note = note,
        createdAtLabel = formatTimestamp(createdAt),
        updatedAtLabel = formatTimestamp(effectiveTimestamp),
        updatedAtPrefix = if (status == PrintJobStatus.CONFIRMED) "已确认" else "已捕获",
        rollLabel = roll?.displayLabel(includeArchivedTag = true),
        rollArchived = roll?.isArchivedRoll() == true,
        externalJobId = externalJobId,
    )
}

private fun FilamentEventItem.toUiModel(): RecentEventUiModel {
    val actionText = when (type) {
        "CALIBRATION" -> "$rollLabel 校准到 ${remainingAfterGrams}g"
        "PRINT_USAGE" -> "$rollLabel 自动同步消耗 ${-deltaGrams}g"
        else -> "$rollLabel 手动登记 ${-deltaGrams}g"
    }
    val sourceLabel = when (source) {
        SyncSourceType.MANUAL -> "手动"
        SyncSourceType.DESKTOP_AGENT -> "桌面同步"
        SyncSourceType.CLOUD -> "云同步"
    }
    return RecentEventUiModel(
        id = id,
        title = actionText,
        note = "$sourceLabel | $note",
        timestampLabel = formatTimestamp(createdAt),
    )
}

private fun SyncSourceType.toUiLabel(): String = when (this) {
    SyncSourceType.MANUAL -> "手动"
    SyncSourceType.DESKTOP_AGENT -> "桌面同步"
    SyncSourceType.CLOUD -> "云同步"
}

private fun PrintJobStatus.toUiLabel(): String = when (this) {
    PrintJobStatus.DRAFT -> "待确认"
    PrintJobStatus.CONFIRMED -> "已确认"
}

private fun FilamentRollEntity.displayLabel(includeArchivedTag: Boolean = false): String {
    val baseLabel = "$colorName $name"
    return if (includeArchivedTag && isArchivedRoll()) {
        "$baseLabel（已删除）"
    } else {
        baseLabel
    }
}

private fun FilamentRollEntity.visibleNotes(): String {
    return if (isArchivedRoll()) {
        notes.removePrefix("[[ARCHIVED]]").trim()
    } else {
        notes
    }
}

private fun DesktopEndpointKind.toUiLabel(): String = when (this) {
    DesktopEndpointKind.NONE -> "未设置"
    DesktopEndpointKind.TAILSCALE -> "Tailscale IP"
    DesktopEndpointKind.MAGIC_DNS -> "MagicDNS"
    DesktopEndpointKind.LAN -> "局域网"
    DesktopEndpointKind.CUSTOM -> "自定义"
}
