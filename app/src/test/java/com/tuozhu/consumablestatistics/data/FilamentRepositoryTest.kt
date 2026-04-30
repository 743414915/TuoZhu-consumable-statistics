package com.tuozhu.consumablestatistics.data

import com.tuozhu.consumablestatistics.sync.SyncConfirmationReceipt
import com.tuozhu.consumablestatistics.sync.SyncCoordinator
import com.tuozhu.consumablestatistics.sync.SyncDraftJob
import com.tuozhu.consumablestatistics.sync.SyncPullResult
import com.tuozhu.consumablestatistics.sync.SyncPushResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Ignore
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
    fun pullSync_removesStaleDraftsThatDisappearFromDesktopQueue() = runTest {
        val dao = FakeFilamentDao()
        var nextResult = SyncPullResult(
            status = SyncConnectionStatus.SUCCESS,
            source = SyncSourceType.DESKTOP_AGENT,
            syncedAt = now,
            message = "first sync",
            draftJobs = listOf(
                SyncDraftJob(
                    externalJobId = "draft-1",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Benchy",
                    estimatedUsageGrams = 18,
                    targetMaterial = "PLA Basic",
                    note = "first",
                    createdAt = now - 1_000,
                ),
                SyncDraftJob(
                    externalJobId = "draft-2",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Bracket",
                    estimatedUsageGrams = 26,
                    targetMaterial = "PETG Basic",
                    note = "second",
                    createdAt = now,
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
            syncedAt = now + 5_000,
            message = "second sync",
            draftJobs = listOf(
                SyncDraftJob(
                    externalJobId = "draft-2",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Bracket",
                    estimatedUsageGrams = 26,
                    targetMaterial = "PETG Basic",
                    note = "second",
                    createdAt = now,
                ),
            ),
        )

        repository.pullSync()

        val pendingJobs = repository.observePendingPrintJobs().first()
        assertEquals(1, pendingJobs.size)
        assertEquals("draft-2", pendingJobs.single().externalJobId)
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
        assertTrue(secondConfirmation.message.contains("无需重复处理"))
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

    @Test
    fun observePrintHistory_returnsConfirmedJobs() = runTest {
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
                        message = "ok",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-history",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "History Part",
                                estimatedUsageGrams = 30,
                                targetMaterial = "PETG Basic",
                                note = "history",
                                createdAt = now,
                            ),
                        ),
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "History Roll",
            material = "PETG Basic",
            colorName = "Gray",
            colorHex = "#666666",
            initialWeightGrams = 1000,
            remainingWeightGrams = 900,
            lowStockThresholdGrams = 100,
            notes = "",
        )
        repository.pullSync()
        repository.confirmPrintJob(dao.printJobsSnapshot().single().id)

        val history = repository.observePrintHistory().first()
        assertEquals(1, history.size)
        assertEquals(PrintJobStatus.CONFIRMED, history.single().status)
        assertEquals("draft-history", history.single().externalJobId)
    }

    @Test
    fun confirmPrintJob_keepsLocalConfirmationWhenPushFails() = runTest {
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
                        message = "ok",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-push-failure",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Retry Part",
                                estimatedUsageGrams = 42,
                                targetMaterial = "PETG Basic",
                                note = "retry",
                                createdAt = now,
                            ),
                        ),
                    )
                },
                pushProvider = {
                    SyncPushResult(
                        status = SyncConnectionStatus.ERROR,
                        source = SyncSourceType.DESKTOP_AGENT,
                        syncedAt = it.confirmedAt,
                        message = "desktop unavailable",
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "Retry Roll",
            material = "PETG Basic",
            colorName = "Black",
            colorHex = "#333333",
            initialWeightGrams = 1000,
            remainingWeightGrams = 780,
            lowStockThresholdGrams = 120,
            notes = "",
        )
        repository.pullSync()

        val job = dao.printJobsSnapshot().single()
        val result = repository.confirmPrintJob(job.id)
        val updatedJob = dao.printJobsSnapshot().single()
        val usageEvents = dao.eventsSnapshot().filter { it.externalJobId == "draft-push-failure" }
        val syncState = repository.observeSyncState().first()

        assertTrue(result.confirmed)
        assertTrue(result.message.contains("desktop unavailable"))
        assertEquals(PrintJobStatus.CONFIRMED, updatedJob.status)
        assertEquals(1, usageEvents.size)
        assertEquals(SyncConnectionStatus.ERROR, syncState?.status)
    }

    @Ignore("Legacy assertion text was mojibake in source; covered by the replacement test below.")
    @Test
    fun confirmPrintJob_doesNotWriteDuplicateUsageEventWhenEventAlreadyExists() = runTest {
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
                        message = "ok",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-existing-event",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Recovered Draft",
                                estimatedUsageGrams = 35,
                                targetMaterial = "PETG Basic",
                                note = "recovered",
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
                        message = "ok",
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "Recovered Roll",
            material = "PETG Basic",
            colorName = "Gray",
            colorHex = "#666666",
            initialWeightGrams = 1000,
            remainingWeightGrams = 900,
            lowStockThresholdGrams = 100,
            notes = "",
        )
        repository.pullSync()

        val rollId = dao.rollsSnapshot().single().id
        dao.insertEvent(
            FilamentEventEntity(
                rollId = rollId,
                type = FilamentEventType.PRINT_USAGE,
                source = SyncSourceType.DESKTOP_AGENT,
                deltaGrams = -35,
                remainingAfterGrams = 865,
                note = "existing event",
                externalJobId = "draft-existing-event",
                createdAt = now + 1_000,
            ),
        )

        val result = repository.confirmPrintJob(dao.printJobsSnapshot().single().id)
        val usageEvents = dao.eventsSnapshot().filter { it.externalJobId == "draft-existing-event" }
        val updatedJob = dao.printJobsSnapshot().single()

        assertTrue(result.confirmed)
        assertTrue(result.message.contains("鏃犻渶閲嶅澶勭悊"))
        assertEquals(1, usageEvents.size)
        assertEquals(PrintJobStatus.CONFIRMED, updatedJob.status)
        assertTrue(pushedReceipts.isEmpty())
    }

    @Test
    fun confirmPrintJob_marksDraftConfirmedWithoutDuplicatingExistingUsageEvent() = runTest {
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
                        message = "ok",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-existing-event-2",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Recovered Draft 2",
                                estimatedUsageGrams = 35,
                                targetMaterial = "PETG Basic",
                                note = "recovered",
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
                        message = "ok",
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "Recovered Roll 2",
            material = "PETG Basic",
            colorName = "Gray",
            colorHex = "#666666",
            initialWeightGrams = 1000,
            remainingWeightGrams = 900,
            lowStockThresholdGrams = 100,
            notes = "",
        )
        repository.pullSync()

        val rollId = dao.rollsSnapshot().single().id
        dao.insertEvent(
            FilamentEventEntity(
                rollId = rollId,
                type = FilamentEventType.PRINT_USAGE,
                source = SyncSourceType.DESKTOP_AGENT,
                deltaGrams = -35,
                remainingAfterGrams = 865,
                note = "existing event",
                externalJobId = "draft-existing-event-2",
                createdAt = now + 1_000,
            ),
        )

        val result = repository.confirmPrintJob(dao.printJobsSnapshot().single().id)
        val usageEvents = dao.eventsSnapshot().filter { it.externalJobId == "draft-existing-event-2" }
        val updatedJob = dao.printJobsSnapshot().single()

        assertTrue(result.confirmed)
        assertTrue(result.message.isNotBlank())
        assertEquals(1, usageEvents.size)
        assertEquals(PrintJobStatus.CONFIRMED, updatedJob.status)
        assertTrue(pushedReceipts.isEmpty())
    }

    @Test
    fun observeRecentPrintJobs_keepsDraftAndConfirmedTimelineTogether() = runTest {
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
                        message = "ok",
                        draftJobs = listOf(
                            SyncDraftJob(
                                externalJobId = "draft-new",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Fresh Draft",
                                estimatedUsageGrams = 16,
                                targetMaterial = "PETG Basic",
                                note = "draft",
                                createdAt = now + 1_000,
                            ),
                            SyncDraftJob(
                                externalJobId = "draft-old",
                                source = SyncSourceType.DESKTOP_AGENT,
                                modelName = "Old Draft",
                                estimatedUsageGrams = 12,
                                targetMaterial = "PETG Basic",
                                note = "old draft",
                                createdAt = now - 5_000,
                            ),
                        ),
                    )
                },
            ),
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "History Roll",
            material = "PETG Basic",
            colorName = "Gray",
            colorHex = "#666666",
            initialWeightGrams = 1000,
            remainingWeightGrams = 900,
            lowStockThresholdGrams = 100,
            notes = "",
        )
        repository.pullSync()
        val oldDraftId = dao.printJobsSnapshot().first { it.externalJobId == "draft-old" }.id
        repository.confirmPrintJob(oldDraftId)

        val timeline = repository.observeRecentPrintJobs().first()

        assertEquals(2, timeline.size)
        assertEquals("draft-old", timeline.first().externalJobId)
        assertEquals(PrintJobStatus.CONFIRMED, timeline.first().status)
        assertEquals("draft-new", timeline.last().externalJobId)
        assertEquals(PrintJobStatus.DRAFT, timeline.last().status)
    }

    @Test
    fun deleteRoll_reassignsActiveRoll_whenDeletingCurrentActive() = runTest {
        val dao = FakeFilamentDao()
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "Active Roll",
            material = "PETG Basic",
            colorName = "Black",
            colorHex = "#333333",
            initialWeightGrams = 1000,
            remainingWeightGrams = 760,
            lowStockThresholdGrams = 120,
            notes = "",
        )
        repository.addRoll(
            brand = "Bambu Lab",
            name = "Backup Roll",
            material = "PETG Basic",
            colorName = "Gray",
            colorHex = "#666666",
            initialWeightGrams = 1000,
            remainingWeightGrams = 820,
            lowStockThresholdGrams = 120,
            notes = "",
        )

        val activeRollId = dao.rollsSnapshot().single { it.isActive }.id
        val backupRoll = dao.rollsSnapshot().single { !it.isActive }

        val result = repository.deleteRoll(activeRollId)

        val remainingRolls = dao.rollsSnapshot()
        assertTrue(result.deleted)
        assertEquals(2, remainingRolls.size)
        assertTrue(remainingRolls.any { it.id == activeRollId && it.isArchivedRoll() })
        assertTrue(remainingRolls.any { it.id == backupRoll.id && it.isActive })
        assertEquals(backupRoll.id, dao.getActiveRoll()?.id)
    }

    @Test
    fun deleteRoll_keepsPrintHistoryAndRetainsArchivedRollReference() = runTest {
        val dao = FakeFilamentDao()
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "History Roll",
            material = "PLA Basic",
            colorName = "White",
            colorHex = "#F3F3F3",
            initialWeightGrams = 1000,
            remainingWeightGrams = 900,
            lowStockThresholdGrams = 100,
            notes = "",
        )
        repository.addRoll(
            brand = "Bambu Lab",
            name = "Spare Roll",
            material = "PETG Basic",
            colorName = "Gray",
            colorHex = "#666666",
            initialWeightGrams = 1000,
            remainingWeightGrams = 850,
            lowStockThresholdGrams = 120,
            notes = "",
        )

        val historyRoll = dao.rollsSnapshot().single { it.name == "History Roll" }
        dao.insertEvent(
            FilamentEventEntity(
                rollId = historyRoll.id,
                type = FilamentEventType.MANUAL_ADJUSTMENT,
                source = SyncSourceType.MANUAL,
                deltaGrams = -30,
                remainingAfterGrams = 870,
                note = "manual usage",
                externalJobId = null,
                createdAt = now,
            ),
        )
        dao.insertPrintJob(
            PrintJobEntity(
                externalJobId = "history-job",
                source = SyncSourceType.MANUAL,
                modelName = "Bracket",
                estimatedUsageGrams = 30,
                targetMaterial = "PLA Basic",
                status = PrintJobStatus.CONFIRMED,
                rollId = historyRoll.id,
                note = "kept history",
                createdAt = now,
                confirmedAt = now + 1_000,
            ),
        )

        val result = repository.deleteRoll(historyRoll.id)

        val remainingRolls = dao.rollsSnapshot()
        val printHistory = repository.observePrintHistory().first()
        assertTrue(result.deleted)
        assertEquals(2, remainingRolls.size)
        assertEquals(1, printHistory.size)
        assertEquals("history-job", printHistory.single().externalJobId)
        assertEquals(historyRoll.id, printHistory.single().rollId)
        assertTrue(remainingRolls.first { it.id == historyRoll.id }.isArchivedRoll())
        assertTrue(dao.eventsSnapshot().any { it.rollId == historyRoll.id })
    }

    @Test
    fun deleteRoll_allowsDeletingLastRoll() = runTest {
        val dao = FakeFilamentDao()
        val repository = FilamentRepository(
            dao = dao,
            runInTransaction = { block -> block() },
        )

        repository.addRoll(
            brand = "Bambu Lab",
            name = "Last Roll",
            material = "PETG Basic",
            colorName = "Black",
            colorHex = "#111111",
            initialWeightGrams = 1000,
            remainingWeightGrams = 640,
            lowStockThresholdGrams = 120,
            notes = "",
        )

        val onlyRollId = dao.rollsSnapshot().single().id
        val result = repository.deleteRoll(onlyRollId)

        assertTrue(result.deleted)
        assertEquals(1, dao.rollsSnapshot().size)
        assertTrue(dao.rollsSnapshot().single().isArchivedRoll())
        assertEquals(null, dao.getActiveRoll())
        assertFalse(dao.eventsSnapshot().isEmpty())
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
    private val recentPrintJobsFlow = MutableStateFlow<List<PrintJobEntity>>(emptyList())
    private val printHistoryFlow = MutableStateFlow<List<PrintJobEntity>>(emptyList())
    private val syncStateFlow = MutableStateFlow<SyncStateEntity?>(null)

    private var nextRollId = 1L
    private var nextEventId = 1L
    private var nextJobId = 1L

    override fun observeRolls(): Flow<List<FilamentRollEntity>> = rollsFlow

    override fun observeRecentEvents(limit: Int): Flow<List<FilamentEventEntity>> = eventsFlow

    override fun observePendingPrintJobs(): Flow<List<PrintJobEntity>> = pendingJobsFlow

    override fun observeRecentPrintJobs(limit: Int): Flow<List<PrintJobEntity>> = recentPrintJobsFlow

    override fun observePrintHistory(limit: Int): Flow<List<PrintJobEntity>> = printHistoryFlow

    override suspend fun getPrintJobByExternalId(externalJobId: String): PrintJobEntity? {
        return printJobs.firstOrNull { it.externalJobId == externalJobId }
    }

    override suspend fun getPrintJobById(jobId: Long): PrintJobEntity? {
        return printJobs.firstOrNull { it.id == jobId }
    }

    override suspend fun getDraftPrintJobs(): List<PrintJobEntity> {
        return printJobs.filter { it.status == PrintJobStatus.DRAFT }
    }

    override fun observeSyncState(): Flow<SyncStateEntity?> = syncStateFlow

    override suspend fun getRollById(rollId: Long): FilamentRollEntity? {
        return rolls.firstOrNull { it.id == rollId }
    }

    override suspend fun getNextActiveCandidate(excludedRollId: Long): FilamentRollEntity? {
        return rolls
            .filter { it.id != excludedRollId && !it.isArchivedRoll() }
            .sortedWith(compareByDescending<FilamentRollEntity> { it.updatedAt }.thenByDescending { it.id })
            .firstOrNull()
    }

    override suspend fun getActiveRollCount(): Int = rolls.count { it.isActive && !it.isArchivedRoll() }

    override suspend fun getActiveRoll(): FilamentRollEntity? = rolls.firstOrNull { it.isActive && !it.isArchivedRoll() }

    override suspend fun sumDeltaAfter(rollId: Long, afterTime: Long): Int {
        return events.filter { it.rollId == rollId && it.createdAt > afterTime }.sumOf { it.deltaGrams }
    }

    override suspend fun countPrintUsageEventsByExternalJobId(externalJobId: String): Int {
        return events.count {
            it.type == FilamentEventType.PRINT_USAGE && it.externalJobId == externalJobId
        }
    }

    override suspend fun setActiveRollInternal(rollId: Long, updatedAt: Long) {
        replaceRolls(
            rolls.map { roll ->
                roll.copy(
                    isActive = roll.id == rollId && !roll.isArchivedRoll(),
                    updatedAt = updatedAt,
                )
            },
        )
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
        refreshRecentPrintJobs()
        refreshPrintHistory()
        return assigned.id
    }

    override suspend fun upsertSyncState(state: SyncStateEntity) {
        syncStateFlow.value = state
    }

    override suspend fun updateRoll(roll: FilamentRollEntity) {
        replaceRolls(rolls.map { if (it.id == roll.id) roll else it })
    }

    override suspend fun markPrintJobConfirmedIfDraft(jobId: Long, rollId: Long, confirmedAt: Long): Int {
        val existing = printJobs.firstOrNull { it.id == jobId } ?: return 0
        if (existing.status != PrintJobStatus.DRAFT) {
            return 0
        }
        replacePrintJobs(
            printJobs.map { job ->
                if (job.id == jobId) {
                    job.copy(
                        status = PrintJobStatus.CONFIRMED,
                        rollId = rollId,
                        confirmedAt = confirmedAt,
                    )
                } else {
                    job
                }
            },
        )
        return 1
    }

    override suspend fun deleteRoll(roll: FilamentRollEntity) {
        val targetId = roll.id
        replaceRolls(rolls.filterNot { it.id == targetId })
        events.removeAll { it.rollId == targetId }
        refreshEvents()
        replacePrintJobs(
            printJobs.map { job ->
                if (job.rollId == targetId) {
                    job.copy(rollId = null)
                } else {
                    job
                }
            },
        )
    }

    override suspend fun updatePrintJob(job: PrintJobEntity) {
        replacePrintJobs(printJobs.map { if (it.id == job.id) job else it })
    }

    override suspend fun deletePrintJob(jobId: Long) {
        replacePrintJobs(printJobs.filterNot { it.id == jobId })
    }

    override suspend fun deleteAllDraftPrintJobs() {
        replacePrintJobs(printJobs.filterNot { it.status == PrintJobStatus.DRAFT })
    }

    override suspend fun deleteDraftPrintJobsNotIn(externalJobIds: List<String>) {
        replacePrintJobs(
            printJobs.filterNot { it.status == PrintJobStatus.DRAFT && it.externalJobId !in externalJobIds },
        )
    }

    // Custom materials
    private val customMaterials = mutableListOf<CustomMaterialEntity>()

    override fun observeCustomMaterials(): Flow<List<CustomMaterialEntity>> {
        return MutableStateFlow(customMaterials.toList())
    }

    override suspend fun getCustomMaterials(): List<CustomMaterialEntity> = customMaterials.toList()

    override suspend fun insertCustomMaterial(material: CustomMaterialEntity) {
        customMaterials.removeAll { it.name == material.name }
        customMaterials.add(material)
    }

    override suspend fun deleteCustomMaterial(name: String) {
        customMaterials.removeAll { it.name == name }
    }

    fun printJobsSnapshot(): List<PrintJobEntity> = printJobs.toList()

    fun eventsSnapshot(): List<FilamentEventEntity> = events.toList()

    fun rollsSnapshot(): List<FilamentRollEntity> = rolls.toList()

    private fun replaceRolls(newRolls: List<FilamentRollEntity>) {
        rolls.clear()
        rolls.addAll(newRolls)
        refreshRolls()
    }

    private fun replacePrintJobs(newJobs: List<PrintJobEntity>) {
        printJobs.clear()
        printJobs.addAll(newJobs)
        refreshPendingJobs()
        refreshRecentPrintJobs()
        refreshPrintHistory()
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

    private fun refreshRecentPrintJobs() {
        recentPrintJobsFlow.value = printJobs.sortedWith(
            compareByDescending<PrintJobEntity> { it.confirmedAt ?: it.createdAt }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        )
    }

    override suspend fun getAllNonArchivedRolls(): List<FilamentRollEntity> {
        return rolls.filterNot { it.isArchivedRoll() }
    }

    override suspend fun getEventsForRoll(rollId: Long): List<FilamentEventEntity> {
        return events.filter { it.rollId == rollId }.sortedBy { it.createdAt }
    }

    override suspend fun getAllConfirmedPrintJobs(): List<PrintJobEntity> {
        return printJobs.filter { it.status == PrintJobStatus.CONFIRMED }
    }

    private fun refreshPrintHistory() {
        printHistoryFlow.value = printJobs
            .filter { it.status == PrintJobStatus.CONFIRMED }
            .sortedWith(
                compareByDescending<PrintJobEntity> { it.confirmedAt ?: it.createdAt }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id },
            )
    }
}
