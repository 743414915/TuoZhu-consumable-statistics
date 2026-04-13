package com.tuozhu.consumablestatistics.sync

import com.tuozhu.consumablestatistics.data.SyncConnectionStatus
import com.tuozhu.consumablestatistics.data.SyncSourceType

data class SyncDraftJob(
    val externalJobId: String,
    val source: SyncSourceType,
    val modelName: String,
    val estimatedUsageGrams: Int,
    val targetMaterial: String?,
    val note: String,
    val createdAt: Long,
)

data class SyncPullResult(
    val status: SyncConnectionStatus,
    val source: SyncSourceType,
    val syncedAt: Long,
    val message: String,
    val draftJobs: List<SyncDraftJob> = emptyList(),
)

data class SyncConfirmationReceipt(
    val externalJobId: String,
    val confirmedAt: Long,
    val targetRollId: Long? = null,
)

data class SyncPushResult(
    val status: SyncConnectionStatus,
    val source: SyncSourceType,
    val syncedAt: Long,
    val message: String,
)

interface SyncCoordinator {
    suspend fun pull(): SyncPullResult

    suspend fun pushConfirmation(receipt: SyncConfirmationReceipt): SyncPushResult {
        return SyncPushResult(
            status = SyncConnectionStatus.SUCCESS,
            source = SyncSourceType.MANUAL,
            syncedAt = System.currentTimeMillis(),
            message = "当前同步源无需回传确认",
        )
    }
}

class LocalSyncCoordinator : SyncCoordinator {
    override suspend fun pull(): SyncPullResult {
        val now = System.currentTimeMillis()
        return SyncPullResult(
            status = SyncConnectionStatus.SUCCESS,
            source = SyncSourceType.DESKTOP_AGENT,
            syncedAt = now,
            message = "已拉取 2 条桌面打印任务草稿，请确认后再记入活动卷。",
            draftJobs = listOf(
                SyncDraftJob(
                    externalJobId = "desktop-demo-benchy",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Benchy Demo",
                    estimatedUsageGrams = 28,
                    targetMaterial = "PLA Basic",
                    note = "来自桌面同步器演示数据",
                    createdAt = now - 15 * 60 * 1000,
                ),
                SyncDraftJob(
                    externalJobId = "desktop-demo-gearbox",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Gearbox Housing",
                    estimatedUsageGrams = 64,
                    targetMaterial = "PETG Basic",
                    note = "等待你选择目标卷后确认",
                    createdAt = now - 5 * 60 * 1000,
                ),
            ),
        )
    }
}
