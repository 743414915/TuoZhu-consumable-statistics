package com.tuozhu.consumablestatistics.sync

import com.tuozhu.consumablestatistics.data.SyncSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDraftValidatorTest {
    private val now = 1775806800000L

    @Test
    fun validateDrafts_rejectsUnsupportedPayloads() {
        val result = SyncDraftValidator.validateDrafts(
            drafts = listOf(
                SyncDraftJob(
                    externalJobId = "valid-job",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Benchy",
                    estimatedUsageGrams = 24,
                    targetMaterial = "PLA Basic",
                    note = "ok",
                    createdAt = now,
                ),
                SyncDraftJob(
                    externalJobId = "bad-material",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Gear",
                    estimatedUsageGrams = 30,
                    targetMaterial = "ABS",
                    note = "bad",
                    createdAt = now,
                ),
                SyncDraftJob(
                    externalJobId = "bad-source",
                    source = SyncSourceType.MANUAL,
                    modelName = "Manual",
                    estimatedUsageGrams = 5,
                    targetMaterial = "PLA Basic",
                    note = "bad",
                    createdAt = now,
                ),
            ),
            now = now,
        )

        assertEquals(1, result.acceptedDrafts.size)
        assertEquals("valid-job", result.acceptedDrafts.single().externalJobId)
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun validateDrafts_normalizesKnownMaterialAliases() {
        val result = SyncDraftValidator.validateDrafts(
            drafts = listOf(
                SyncDraftJob(
                    externalJobId = "alias-job",
                    source = SyncSourceType.DESKTOP_AGENT,
                    modelName = "Alias",
                    estimatedUsageGrams = 42,
                    targetMaterial = "PETG 基础",
                    note = "alias",
                    createdAt = now,
                ),
            ),
            now = now,
        )

        assertEquals(1, result.acceptedDrafts.size)
        assertEquals("PETG Basic", result.acceptedDrafts.single().targetMaterial)
    }

    @Test
    fun appendWarningSummary_includesRejectedCount() {
        val summary = SyncDraftValidator.appendWarningSummary(
            baseMessage = "同步完成",
            warnings = listOf("w1", "w2"),
        )

        assertTrue(summary.contains("同步完成"))
        assertTrue(summary.contains("已忽略 2 条无效草稿"))
    }
}
