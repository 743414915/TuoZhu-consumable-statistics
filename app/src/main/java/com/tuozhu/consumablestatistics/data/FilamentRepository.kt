package com.tuozhu.consumablestatistics.data

import androidx.room.withTransaction
import com.tuozhu.consumablestatistics.domain.SupportedMaterials
import com.tuozhu.consumablestatistics.domain.WeightMath
import com.tuozhu.consumablestatistics.sync.LocalSyncCoordinator
import com.tuozhu.consumablestatistics.sync.SyncConfirmationReceipt
import com.tuozhu.consumablestatistics.sync.SyncCoordinator
import com.tuozhu.consumablestatistics.sync.SyncDraftJob
import com.tuozhu.consumablestatistics.sync.SyncDraftValidator
import com.tuozhu.consumablestatistics.sync.SyncPullResult
import kotlinx.coroutines.flow.Flow

data class RollSnapshot(
    val roll: FilamentRollEntity,
    val estimatedRemainingGrams: Int,
)

data class PrintJobConfirmationResult(
    val confirmed: Boolean,
    val message: String,
)

data class RollDeletionResult(
    val deleted: Boolean,
    val message: String,
)

class FilamentRepository(
    private val dao: FilamentDao,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val syncCoordinator: SyncCoordinator = LocalSyncCoordinator(),
) {
    constructor(
        dao: FilamentDao,
        database: ConsumableDatabase,
        syncCoordinator: SyncCoordinator = LocalSyncCoordinator(),
    ) : this(
        dao = dao,
        runInTransaction = { block -> database.withTransaction { block() } },
        syncCoordinator = syncCoordinator,
    )

    fun observeRolls(): Flow<List<FilamentRollEntity>> = dao.observeRolls()

    fun observeRecentEvents(limit: Int = 20): Flow<List<FilamentEventEntity>> = dao.observeRecentEvents(limit)

    fun observePendingPrintJobs(): Flow<List<PrintJobEntity>> = dao.observePendingPrintJobs()

    fun observeRecentPrintJobs(limit: Int = 40): Flow<List<PrintJobEntity>> = dao.observeRecentPrintJobs(limit)

    fun observePrintHistory(limit: Int = 30): Flow<List<PrintJobEntity>> = dao.observePrintHistory(limit)

    fun observeSyncState(): Flow<SyncStateEntity?> = dao.observeSyncState()

    suspend fun addRoll(
        brand: String,
        name: String,
        material: String,
        colorName: String,
        colorHex: String,
        initialWeightGrams: Int,
        remainingWeightGrams: Int,
        lowStockThresholdGrams: Int,
        notes: String,
    ) {
        val now = System.currentTimeMillis()
        val clampedRemaining = WeightMath.clampRemaining(remainingWeightGrams, initialWeightGrams)
        inTransaction {
            val shouldActivate = dao.getActiveRollCount() == 0
            val rollId = dao.insertRoll(
                FilamentRollEntity(
                    brand = brand.trim(),
                    name = name.trim(),
                    material = SupportedMaterials.normalize(material) ?: material.trim(),
                    colorName = colorName.trim(),
                    colorHex = colorHex.trim().ifBlank { "#D86A3D" },
                    initialWeightGrams = initialWeightGrams,
                    lowStockThresholdGrams = lowStockThresholdGrams,
                    lastCalibrationWeightGrams = clampedRemaining,
                    lastCalibrationAt = now,
                    isActive = shouldActivate,
                    notes = notes.trim(),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.insertEvent(
                FilamentEventEntity(
                    rollId = rollId,
                    type = FilamentEventType.CALIBRATION,
                    source = SyncSourceType.MANUAL,
                    deltaGrams = 0,
                    remainingAfterGrams = clampedRemaining,
                    note = "初始建档",
                    externalJobId = null,
                    createdAt = now,
                ),
            )
        }
    }

    suspend fun consumeRoll(rollId: Long, grams: Int, note: String) {
        if (grams <= 0) return
        val roll = dao.getRollById(rollId) ?: return
        val now = System.currentTimeMillis()
        val currentRemaining = calculateRemaining(roll)
        val updatedRemaining = WeightMath.clampRemaining(currentRemaining - grams, roll.initialWeightGrams)
        dao.updateRollAndInsertEvent(
            roll = roll.copy(updatedAt = now),
            event = FilamentEventEntity(
                rollId = rollId,
                type = FilamentEventType.MANUAL_ADJUSTMENT,
                source = SyncSourceType.MANUAL,
                deltaGrams = updatedRemaining - currentRemaining,
                remainingAfterGrams = updatedRemaining,
                note = note.trim().ifBlank { "手动登记耗材" },
                externalJobId = null,
                createdAt = now,
            ),
        )
    }

    suspend fun recalibrateRoll(rollId: Long, newRemainingGrams: Int, note: String) {
        val roll = dao.getRollById(rollId) ?: return
        val now = System.currentTimeMillis()
        val clampedRemaining = WeightMath.clampRemaining(newRemainingGrams, roll.initialWeightGrams)
        dao.updateRollAndInsertEvent(
            roll = roll.copy(
                lastCalibrationWeightGrams = clampedRemaining,
                lastCalibrationAt = now,
                updatedAt = now,
            ),
            event = FilamentEventEntity(
                rollId = rollId,
                type = FilamentEventType.CALIBRATION,
                source = SyncSourceType.MANUAL,
                deltaGrams = 0,
                remainingAfterGrams = clampedRemaining,
                note = note.trim().ifBlank { "称重校准" },
                externalJobId = null,
                createdAt = now,
            ),
        )
    }

    suspend fun setActiveRoll(rollId: Long) {
        dao.setActiveRollInternal(rollId, System.currentTimeMillis())
    }

    suspend fun deleteRoll(rollId: Long): RollDeletionResult {
        val roll = dao.getRollById(rollId)
            ?: return RollDeletionResult(false, "要删除的耗材卷不存在")
        if (roll.isArchivedRoll()) {
            return RollDeletionResult(true, "该耗材卷已从当前库存移除")
        }

        val now = System.currentTimeMillis()
        var replacement: FilamentRollEntity? = null

        inTransaction {
            if (roll.isActive) {
                replacement = dao.getNextActiveCandidate(rollId)
            }
            dao.updateRoll(
                roll.copy(
                    isActive = false,
                    notes = archiveRollNotes(roll.notes),
                    updatedAt = now,
                ),
            )
            replacement?.let { next ->
                dao.setActiveRollInternal(next.id, now)
            }
        }

        val message = when {
            replacement != null -> "已将 ${roll.colorName} ${roll.name} 移出当前库存，并切换到 ${replacement!!.colorName} ${replacement!!.name}"
            roll.isActive -> "已将 ${roll.colorName} ${roll.name} 移出当前库存，当前没有活动卷"
            else -> "已将 ${roll.colorName} ${roll.name} 移出当前库存"
        }
        return RollDeletionResult(true, message)
    }

    suspend fun pullSync(): SyncPullResult {
        return try {
            val result = syncCoordinator.pull()
            val validation = SyncDraftValidator.validateDrafts(result.draftJobs)
            val sanitizedResult = result.copy(
                message = SyncDraftValidator.appendWarningSummary(result.message, validation.warnings),
                draftJobs = validation.acceptedDrafts,
            )
            inTransaction {
                upsertDraftJobs(sanitizedResult.draftJobs)
                dao.upsertSyncState(
                    SyncStateEntity(
                        status = sanitizedResult.status,
                        lastSyncSource = sanitizedResult.source,
                        lastSyncAt = sanitizedResult.syncedAt,
                        lastMessage = sanitizedResult.message,
                    ),
                )
            }
            sanitizedResult
        } catch (exception: Exception) {
            val failedAt = System.currentTimeMillis()
            val fallback = SyncPullResult(
                status = SyncConnectionStatus.ERROR,
                source = SyncSourceType.DESKTOP_AGENT,
                syncedAt = failedAt,
                message = "同步失败：${exception.message ?: "未知错误"}",
            )
            dao.upsertSyncState(
                SyncStateEntity(
                    status = fallback.status,
                    lastSyncSource = fallback.source,
                    lastSyncAt = fallback.syncedAt,
                    lastMessage = fallback.message,
                ),
            )
            fallback
        }
    }

    suspend fun deletePrintJob(jobId: Long): Boolean {
        val job = dao.getPrintJobById(jobId) ?: return false
        if (job.status != PrintJobStatus.DRAFT) return false
        dao.deletePrintJob(jobId)
        return true
    }

    suspend fun confirmPrintJob(jobId: Long, targetRollId: Long? = null): PrintJobConfirmationResult {
        val job = dao.getPrintJobById(jobId)
            ?: return PrintJobConfirmationResult(false, "打印任务不存在")
        if (job.status == PrintJobStatus.CONFIRMED) {
            return PrintJobConfirmationResult(true, "该任务已确认，无需重复处理")
        }

        val rollId = targetRollId ?: job.rollId ?: dao.getActiveRoll()?.id
            ?: return PrintJobConfirmationResult(false, "请先设置活动耗材卷")
        val roll = dao.getRollById(rollId)
            ?: return PrintJobConfirmationResult(false, "目标耗材卷不存在")
        if (roll.isArchivedRoll()) {
            return PrintJobConfirmationResult(false, "目标耗材卷已删除，请重新选择活动卷")
        }

        val expectedMaterial = SupportedMaterials.normalize(job.targetMaterial)
        val actualMaterial = SupportedMaterials.normalize(roll.material)
        if (expectedMaterial != null && actualMaterial != expectedMaterial) {
            return PrintJobConfirmationResult(
                false,
                "任务需要 $expectedMaterial，当前活动卷是 ${actualMaterial ?: roll.material}",
            )
        }

        val now = System.currentTimeMillis()
        var localFailureMessage: String? = null
        var confirmationReceipt: SyncConfirmationReceipt? = null
        val currentRemaining = calculateRemaining(roll)
        if (currentRemaining < job.estimatedUsageGrams) {
            return PrintJobConfirmationResult(
                false,
                "当前活动卷仅剩 ${currentRemaining}g，不足以扣减 ${job.estimatedUsageGrams}g",
            )
        }

        var confirmedNow = false
        inTransaction {
            val latestJob = dao.getPrintJobById(jobId)
            if (latestJob == null) {
                localFailureMessage = "鎵撳嵃浠诲姟涓嶅瓨鍦?"
                return@inTransaction
            }
            val latestRoll = dao.getRollById(rollId)
            if (latestRoll == null) {
                localFailureMessage = "鐩爣鑰楁潗鍗蜂笉瀛樺湪"
                return@inTransaction
            }
            if (latestRoll.isArchivedRoll()) {
                localFailureMessage = "鐩爣鑰楁潗鍗峰凡鍒犻櫎锛岃閲嶆柊閫夋嫨娲诲姩鍗?"
                return@inTransaction
            }
            if (latestJob.status == PrintJobStatus.CONFIRMED ||
                dao.countPrintUsageEventsByExternalJobId(latestJob.externalJobId) > 0
            ) {
                dao.markPrintJobConfirmedIfDraft(jobId, rollId, latestJob.confirmedAt ?: now)
                return@inTransaction
            }

            val refreshedRemaining = calculateRemaining(latestRoll)
            if (refreshedRemaining < latestJob.estimatedUsageGrams) {
                localFailureMessage = "褰撳墠娲诲姩鍗蜂粎鍓?${refreshedRemaining}g锛屼笉瓒充互鎵ｅ噺 ${latestJob.estimatedUsageGrams}g"
                return@inTransaction
            }
            val updatedRemaining = refreshedRemaining - latestJob.estimatedUsageGrams
            confirmedNow = dao.markPrintJobConfirmedIfDraft(jobId, rollId, now) == 1
            if (confirmedNow) {
                dao.updateRoll(latestRoll.copy(updatedAt = now))
                dao.insertEvent(
                    FilamentEventEntity(
                        rollId = rollId,
                        type = FilamentEventType.PRINT_USAGE,
                        source = latestJob.source,
                        deltaGrams = updatedRemaining - refreshedRemaining,
                        remainingAfterGrams = updatedRemaining,
                        note = job.note.ifBlank { "确认打印任务" },
                        externalJobId = latestJob.externalJobId,
                        createdAt = now,
                    ),
                )
                confirmationReceipt = SyncConfirmationReceipt(
                    externalJobId = latestJob.externalJobId,
                    confirmedAt = now,
                    targetRollId = rollId,
                )
            }
        }

        if (localFailureMessage != null) {
            return PrintJobConfirmationResult(false, localFailureMessage!!)
        }
        if (!confirmedNow) {
            return PrintJobConfirmationResult(true, "该任务已确认，无需重复处理")
        }

        val pushResult = syncCoordinator.pushConfirmation(confirmationReceipt!!)
        dao.upsertSyncState(
            SyncStateEntity(
                status = pushResult.status,
                lastSyncSource = pushResult.source,
                lastSyncAt = pushResult.syncedAt,
                lastMessage = pushResult.message,
            ),
        )

        return if (pushResult.status == SyncConnectionStatus.SUCCESS) {
            PrintJobConfirmationResult(true, "任务已确认并同步回桌面端")
        } else {
            PrintJobConfirmationResult(true, "任务已记入活动卷，但桌面回执未成功：${pushResult.message}")
        }
    }

    suspend fun calculateRemaining(roll: FilamentRollEntity): Int {
        val deltaAfterCalibration = dao.sumDeltaAfter(roll.id, roll.lastCalibrationAt)
        return WeightMath.clampRemaining(
            roll.lastCalibrationWeightGrams + deltaAfterCalibration,
            roll.initialWeightGrams,
        )
    }

    private suspend fun upsertDraftJobs(drafts: List<SyncDraftJob>) {
        val incomingIds = drafts.map { it.externalJobId }.toSet()
        val localDrafts = dao.getDraftPrintJobs()
        if (incomingIds.isEmpty()) {
            if (localDrafts.isNotEmpty()) {
                dao.deleteAllDraftPrintJobs()
            }
        } else if (localDrafts.any { it.externalJobId !in incomingIds }) {
            dao.deleteDraftPrintJobsNotIn(incomingIds.toList())
        }

        drafts.forEach { draft ->
            val targetMaterial = SupportedMaterials.normalize(draft.targetMaterial)
            val existing = dao.getPrintJobByExternalId(draft.externalJobId)
            if (existing == null) {
                dao.insertPrintJob(
                    PrintJobEntity(
                        externalJobId = draft.externalJobId,
                        source = draft.source,
                        modelName = draft.modelName,
                        estimatedUsageGrams = draft.estimatedUsageGrams,
                        targetMaterial = targetMaterial,
                        status = PrintJobStatus.DRAFT,
                        rollId = null,
                        note = SyncDraftValidator.appendMaterialNote(draft.note, targetMaterial),
                        createdAt = draft.createdAt,
                        confirmedAt = null,
                    ),
                )
            } else if (existing.status == PrintJobStatus.DRAFT) {
                dao.updatePrintJob(
                    existing.copy(
                        source = draft.source,
                        modelName = draft.modelName,
                        estimatedUsageGrams = draft.estimatedUsageGrams,
                        targetMaterial = targetMaterial,
                        note = SyncDraftValidator.appendMaterialNote(draft.note, targetMaterial),
                        createdAt = draft.createdAt,
                    ),
                )
            }
        }
    }

    private suspend fun inTransaction(block: suspend () -> Unit) {
        runInTransaction(block)
    }

    suspend fun buildExportJson(): String {
        val rolls = dao.getAllNonArchivedRolls()
        val eventsByRoll = rolls.associate { roll ->
            roll.id to dao.getEventsForRoll(roll.id)
        }
        val printJobs = dao.getAllConfirmedPrintJobs()
        return DataExportImport.toExportJson(rolls, eventsByRoll, printJobs)
    }

    suspend fun importFromJson(json: String): ImportResult {
        val importData = DataExportImport.parseImportJson(json)
            ?: return ImportResult(0, 0)

        var rollCount = 0
        var printJobCount = 0

        inTransaction {
            val now = System.currentTimeMillis()
            var lastActiveRollId: Long? = null

            importData.rolls.forEach { exportedRoll ->
                val rollId = dao.insertRoll(
                    FilamentRollEntity(
                        brand = exportedRoll.brand,
                        name = exportedRoll.name,
                        material = exportedRoll.material,
                        colorName = exportedRoll.colorName,
                        colorHex = exportedRoll.colorHex,
                        initialWeightGrams = exportedRoll.initialWeightGrams,
                        lowStockThresholdGrams = exportedRoll.lowStockThresholdGrams,
                        lastCalibrationWeightGrams = exportedRoll.lastCalibrationWeightGrams,
                        lastCalibrationAt = exportedRoll.lastCalibrationAt,
                        isActive = exportedRoll.isActive,
                        notes = exportedRoll.notes,
                        createdAt = exportedRoll.createdAt,
                        updatedAt = now,
                    ),
                )
                if (exportedRoll.isActive) {
                    lastActiveRollId = rollId
                }
                exportedRoll.events.forEach { exportedEvent ->
                    dao.insertEvent(
                        FilamentEventEntity(
                            rollId = rollId,
                            type = exportedEvent.type,
                            source = exportedEvent.source,
                            deltaGrams = exportedEvent.deltaGrams,
                            remainingAfterGrams = exportedEvent.remainingAfterGrams,
                            note = exportedEvent.note,
                            externalJobId = exportedEvent.externalJobId,
                            createdAt = exportedEvent.createdAt,
                        ),
                    )
                }
                rollCount++
            }

            if (lastActiveRollId != null) {
                dao.setActiveRollInternal(lastActiveRollId!!, now)
            }

            importData.printJobs.forEach { exportedJob ->
                val existing = dao.getPrintJobByExternalId(exportedJob.externalJobId)
                if (existing == null) {
                    dao.insertPrintJob(
                        PrintJobEntity(
                            externalJobId = exportedJob.externalJobId,
                            source = exportedJob.source,
                            modelName = exportedJob.modelName,
                            estimatedUsageGrams = exportedJob.estimatedUsageGrams,
                            targetMaterial = exportedJob.targetMaterial,
                            status = exportedJob.status,
                            rollId = null,
                            note = exportedJob.note,
                            createdAt = exportedJob.createdAt,
                            confirmedAt = exportedJob.confirmedAt,
                        ),
                    )
                    printJobCount++
                }
            }
        }

        return ImportResult(rollCount = rollCount, printJobCount = printJobCount)
    }

    private fun archiveRollNotes(notes: String): String {
        if (notes.startsWith(ARCHIVED_ROLL_NOTE_PREFIX)) {
            return notes
        }
        val trimmed = notes.trim()
        return if (trimmed.isBlank()) {
            ARCHIVED_ROLL_NOTE_PREFIX
        } else {
            "$ARCHIVED_ROLL_NOTE_PREFIX $trimmed"
        }
    }
}
