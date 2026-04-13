package com.tuozhu.consumablestatistics.sync

import com.tuozhu.consumablestatistics.data.SyncSourceType
import com.tuozhu.consumablestatistics.domain.SupportedMaterials

data class SyncDraftValidationResult(
    val acceptedDrafts: List<SyncDraftJob>,
    val warnings: List<String>,
)

object SyncDraftValidator {
    private const val earliestAllowedCreatedAt = 1577836800000L
    private const val maxFutureDriftMs = 24 * 60 * 60 * 1000L

    fun validateDrafts(
        drafts: List<SyncDraftJob>,
        now: Long = System.currentTimeMillis(),
    ): SyncDraftValidationResult {
        val warnings = mutableListOf<String>()
        val acceptedDrafts = drafts.mapIndexedNotNull { index, draft ->
            val problem = validateDraft(draft, now)
            if (problem == null) {
                draft.copy(
                    externalJobId = draft.externalJobId.trim(),
                    modelName = draft.modelName.trim(),
                    targetMaterial = SupportedMaterials.normalize(draft.targetMaterial),
                    note = draft.note.trim(),
                )
            } else {
                warnings += "草稿[$index] ${draft.externalJobId.ifBlank { "<blank>" }} 已忽略: $problem"
                null
            }
        }
        return SyncDraftValidationResult(
            acceptedDrafts = acceptedDrafts,
            warnings = warnings,
        )
    }

    fun appendMaterialNote(
        note: String,
        targetMaterial: String?,
    ): String {
        return buildString {
            append(note.trim())
            SupportedMaterials.normalize(targetMaterial)?.let {
                if (isNotBlank()) {
                    append(" | ")
                }
                append("目标材料 $it")
            }
        }
    }

    fun appendWarningSummary(
        baseMessage: String,
        warnings: List<String>,
    ): String {
        if (warnings.isEmpty()) {
            return baseMessage
        }
        val summary = "已忽略 ${warnings.size} 条无效草稿"
        return if (baseMessage.isBlank()) summary else "$baseMessage $summary"
    }

    private fun validateDraft(
        draft: SyncDraftJob,
        now: Long,
    ): String? {
        if (draft.externalJobId.isBlank()) {
            return "externalJobId 不能为空"
        }
        if (draft.modelName.isBlank()) {
            return "modelName 不能为空"
        }
        if (draft.estimatedUsageGrams <= 0) {
            return "estimatedUsageGrams 必须大于 0"
        }
        if (!SupportedMaterials.isSupported(draft.targetMaterial)) {
            return "targetMaterial 仅支持 ${SupportedMaterials.all.joinToString()}"
        }
        if (draft.source == SyncSourceType.MANUAL) {
            return "source 不能是 MANUAL"
        }
        if (draft.createdAt !in earliestAllowedCreatedAt..(now + maxFutureDriftMs)) {
            return "createdAt 超出允许范围"
        }
        return null
    }
}
