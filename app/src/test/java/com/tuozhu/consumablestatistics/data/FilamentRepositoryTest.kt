package com.tuozhu.consumablestatistics.data

import com.tuozhu.consumablestatistics.sync.SyncConfirmationReceipt
import com.tuozhu.consumablestatistics.sync.SyncCoordinator
import com.tuozhu.consumablestatistics.sync.SyncDraftJob
import com.tuozhu.consumablestatistics.sync.SyncPullResult
import com.tuozhu.consumablestatistics.sync.SyncPushResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilamentRepositoryTest {
    private val now = 1775806800000L

    @Test
    fun pullSync_filtersInvalidDraftsBeforePersisting() = runTest {
        val dao = FakeFilamentDao()
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
            syncCoordinator = FakeSyncCoordinator(
                pullProvider = {
                    SyncPullResult(
                        status = SyncConnectionStatus.SUCCESS,
                        source = SyncSourceType.DESKTOP_AGENT,
                        syncedAt = now,
                        message = "同步完成",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-valid",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Benchy",
                                estimatedUsageGrams = 28,
                                targetMaterial = "PLA Basic",
                                note = "valid",
                                createdAt = now,
                            ),
                            SyncDraftJob(
                                externalJobId = "draft-invalid-material",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Bad Material",
                                estimatedUsageGrams = 20,
                                targetMaterial = "ABS",
                                note = "invalid",
                                createdAt = now,
                            ),
                            SyncDraftJob(
                                externalJobId = "draft-invalid-usage",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Bad Usage",
                                estimatedUsageGrams = 0,
                                targetMaterial = "PLA Basic",
                                note = "invalid",
                                createdAt = now,
                            ),
                        ),
                    )
                },
            ),
        )

        val result = repository.pullSync()
        val pendingJobs = repository.observePendingPrintJobs().first()

        assertEquals(1, result.draftJobs.size)
        assertEquals(1, pendingJobs.size)
        assertEquals("draft-valid", pendingJobs.single().externalJobId)
        assertTrue(result.message.contains("已忽略 2 条无效草稿"))
    }

    @Test
    fun pullSync_updatesExistingDraftWithoutDuplicatingExternalJob() = runTest {
        val dao = FakeFilamentDao()
        var nextResult = SyncPullResult(
            status = SyncConnectionStatus.SUCCESS,
            source = SyncSourceType.DESKTOP_AGENT,
            syncedAt = now,
            message = "第一次同步",
            draftJobs = listOf(
                SyncDraftJob(
                    externalJobId = "draft-1",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Benchy",
                    estimatedUsageGrams = 18,
                    targetMaterial = "PLA Basic",
                    note = "第一版",
                    createdAt = now - 1_000,
                ),
            ),
        )
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
            syncCoordinator = FakeSyncCoordinator(pullProvider = { nextResult }),
        )

        repository.pullSync()
        nextResult = nextResult.copy(
            syncedAt = now + 10_000,
            message = "第二次同步",
            draftJobs = listOf(
                SyncDraftJob(
                    externalJobId = "draft-1",
                    source = SyncSourceType.CLOUD,
                    modelName = "Benchy v2",
                    estimatedUsageGrams = 22,
                    targetMaterial = "PLA Silk",
                    note = "第二版",
                    createdAt = now + 2_000,
                ),
            ),
        )

        repository.pullSync()

        val jobs = dao.printJobsSnapshot()
        assertEquals(1, jobs.size)
        assertEquals(PrintJobStatus.DRAFT, jobs.single().status)
        assertEquals(SyncSourceType.CLOUD, jobs.single().source)
        assertEquals("Benchy v2", jobs.single().modelName)
        assertEquals(22, jobs.single().estimatedUsageGrams)
        assertEquals("PLA Silk", jobs.single().targetMaterial)
        assertEquals(now + 2_000, jobs.single().createdAt)
        assertEquals("第二版 | 目标材料 PLA Silk", jobs.single().note)
    }

    @Test
    fun confirmPrintJob_writesSingleUsageEventAndPushesReceipt() = runTest {
        val dao = FakeFilamentDao()
        val pushedReceipts = mutableListOf<SyncConfirmationReceipt>()
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
            syncCoordinator = FakeSyncCoordinator(
                pullProvider = {
                    SyncPullResult(
                        status = SyncConnectionStatus.SUCCESS,
                        source = SyncSourceType.DESKTOP_AGENT,
                        syncedAt = now,
                        message = "同步完成",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-confirm",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Functional Part",
                                estimatedUsageGrams = 120,
                                targetMaterial = "PETG Basic",
                                note = "待确认",
                                createdAt = now,
                            ),
                        ),
                    )
                },
                pushProvider = { receipt ->
                    pushedReceipts += receipt
                    SyncPushResult(
                        status = SyncConnectionStatus.SUCCESS,
                        source = SyncSourceType.DESKTOP_AGENT,
                        syncedAt = receipt.confirmedAt,
                        message = "回执成功",
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "拓竹 Bambu Lab",
            name = "PETG 主力卷",
            material = "PETG Basic",
            colorName = "黑色",
            colorHex = "#333333",
            initialWeightGrams = 1000,
            remainingWeightGrams = 800,
            lowStockThresholdGrams = 150,
            notes = "",
        )
        repository.pullSync()

        val job = dao.printJobsSnapshot().single()
        val firstConfirmation = repository.confirmPrintJob(job.id)
        val secondConfirmation = repository.confirmPrintJob(job.id)

        val updatedJob = dao.printJobsSnapshot().single()
        val usageEvents = dao.eventsSnapshot().filter {
            it.type == FilamentEventType.PRINT_USAGE && it.externalJobId == "draft-confirm"
        }
        val pendingJobs = repository.observePendingPrintJobs().first()

        assertTrue(firstConfirmation.confirmed)
        assertTrue(secondConfirmation.confirmed)
        assertEquals(PrintJobStatus.CONFIRMED, updatedJob.status)
        assertEquals(1, usageEvents.size)
        assertEquals(-120, usageEvents.single().deltaGrams)
        assertEquals(680, usageEvents.single().remainingAfterGrams)
        assertFalse(pendingJobs.any { it.externalJobId == "draft-confirm" })
        assertEquals(1, pushedReceipts.size)
        assertEquals("draft-confirm", pushedReceipts.single().externalJobId)
    }

    @Test
    fun confirmPrintJob_rejectsMaterialMismatch() = runTest {
        val dao = FakeFilamentDao()
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
            syncCoordinator = FakeSyncCoordinator(
                pullProvider = {
                    SyncPullResult(
                        status = SyncConnectionStatus.SUCCESS,
                        source = SyncSourceType.DESKTOP_AGENT,
                        syncedAt = now,
                        message = "同步完成",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-mismatch",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Mismatch",
                                estimatedUsageGrams = 50,
                                targetMaterial = "PLA Basic",
                                note = "待确认",
                                createdAt = now,
                            ),
                        ),
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "拓竹 Bambu Lab",
            name = "PETG 主力卷",
            material = "PETG Basic",
            colorName = "黑色",
            colorHex = "#333333",
            initialWeightGrams = 1000,
            remainingWeightGrams = 700,
            lowStockThresholdGrams = 150,
            notes = "",
        )
        repository.pullSync()

        val result = repository.confirmPrintJob(dao.printJobsSnapshot().single().id)

        assertFalse(result.confirmed)
        assertTrue(result.message.contains("PLA Basic"))
        assertEquals(0, dao.eventsSnapshot().count { it.type == FilamentEventType.PRINT_USAGE })
    }
}

private class FakeSyncCoordinator(
    private val pullProvider: () -> SyncPullResult,
    private val pushProvider: (SyncConfirmationReceipt) -> SyncPushResult = {
        SyncPushResult(
            status = SyncConnectionStatus.SUCCESS,
            source = SyncSourceType.DESKTOP_AGENT,
            syncedAt = it.confirmedAt,
            message = "ok",
        )
    },
) : SyncCoordinator {
    override suspend fun pull(): SyncPullResult = pullProvider()

    override suspend fun pushConfirmation(receipt: SyncConfirmationReceipt): SyncPushResult {
        return pushProvider(receipt)
    }
}

private class FakeFilamentDao : FilamentDao {
    private val rolls = mutableListOf<FilamentRollEntity>()
    private val events = mutableListOf<FilamentEventEntity>()
    private val printJobs = mutableListOf<PrintJobEntity>()

    private val rollsFlow = MutableStateFlow<List<FilamentRollEntity>>(emptyList())
    private val eventsFlow = MutableStateFlow<List<FilamentEventEntity>>(emptyList())
    private val pendingJobsFlow = MutableStateFlow<List<PrintJobEntity>>(emptyList())
    private val syncStateFlow = MutableStateFlow<SyncStateEntity?>(null)

    private var nextRollId = 1L
    private var nextEventId = 1L
    private var nextJobId = 1L

    override fun observeRolls(): Flow<List<FilamentRollEntity>> = rollsFlow

    override fun observeRecentEvents(limit: Int): Flow<List<FilamentEventEntity>> = eventsFlow

    override fun observePendingPrintJobs(): Flow<List<PrintJobEntity>> = pendingJobsFlow

    override suspend fun getPrintJobByExternalId(externalJobId: String): PrintJobEntity? {
        return printJobs.firstOrNull { it.externalJobId == externalJobId }
    }

    override suspend fun getPrintJobById(jobId: Long): PrintJobEntity? {
        return printJobs.firstOrNull { it.id == jobId }
    }

    override fun observeSyncState(): Flow<SyncStateEntity?> = syncStateFlow

    override suspend fun getRollById(rollId: Long): FilamentRollEntity? {
        return rolls.firstOrNull { it.id == rollId }
    }

    override suspend fun getActiveRollCount(): Int = rolls.count { it.isActive }

    override suspend fun getActiveRoll(): FilamentRollEntity? = rolls.firstOrNull { it.isActive }

    override suspend fun sumDeltaAfter(rollId: Long, afterTime: Long): Int {
        return events.filter { it.rollId == rollId && it.createdAt > afterTime }.sumOf { it.deltaGrams }
    }

    override suspend fun setActiveRollInternal(rollId: Long, updatedAt: Long) {
        replaceRolls(rolls.map { roll -> roll.copy(isActive = roll.id == rollId, updatedAt = updatedAt) })
    }

    override suspend fun insertRoll(roll: FilamentRollEntity): Long {
        val assigned = if (roll.id == 0L) roll.copy(id = nextRollId++) else roll
        rolls += assigned
        refreshRolls()
        return assigned.id
    }

    override suspend fun insertEvent(event: FilamentEventEntity): Long {
        val assigned = if (event.id == 0L) event.copy(id = nextEventId++) else event
        events += assigned
        refreshEvents()
        return assigned.id
    }

    override suspend fun insertPrintJob(job: PrintJobEntity): Long {
        val assigned = if (job.id == 0L) job.copy(id = nextJobId++) else job
        printJobs += assigned
        refreshPendingJobs()
        return assigned.id
    }

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        syncStateFlow.value = state
    }

    override suspend fun updateRoll(roll: FilamentRollEntity) {
        replaceRolls(rolls.map { if (it.id == roll.id) roll else it })
    }

    override suspend fun updatePrintJob(job: PrintJobEntity) {
        replacePrintJobs(printJobs.map { if (it.id == job.id) job else it })
    }

    fun printJobsSnapshot(): List<PrintJobEntity> = printJobs.toList()

    fun eventsSnapshot(): List<FilamentEventEntity> = events.toList()

    private fun replaceRolls(newRolls: List<FilamentRollEntity>) {
        rolls.clear()
        rolls.addAll(newRolls)
        refreshRolls()
    }

    private fun replacePrintJobs(newJobs: List<PrintJobEntity>) {
        printJobs.clear()
        printJobs.addAll(newJobs)
        refreshPendingJobs()
    }

    private fun refreshRolls() {
        rollsFlow.value = rolls.sortedWith(
            compareByDescending<FilamentRollEntity> { it.isActive }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.id },
        )
    }

    private fun refreshEvents() {
        eventsFlow.value = events.sortedWith(
            compareByDescending<FilamentEventEntity> { it.createdAt }
                .thenByDescending { it.id },
        )
    }

    private fun refreshPendingJobs() {
        pendingJobsFlow.value = printJobs
            .filter { it.status == PrintJobStatus.DRAFT }
            .sortedWith(
                compareByDescending<PrintJobEntity> { it.createdAt }
                    .thenByDescending { it.id },
            )
    }
}
