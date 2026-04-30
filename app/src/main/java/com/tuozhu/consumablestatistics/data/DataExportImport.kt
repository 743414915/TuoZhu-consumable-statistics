package com.tuozhu.consumablestatistics.data

import org.json.JSONArray
import org.json.JSONObject

data class ExportRoll(
    val brand: String,
    val name: String,
    val material: String,
    val colorName: String,
    val colorHex: String,
    val initialWeightGrams: Int,
    val lowStockThresholdGrams: Int,
    val lastCalibrationWeightGrams: Int,
    val lastCalibrationAt: Long,
    val isActive: Boolean,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val events: List<ExportEvent>,
)

data class ExportEvent(
    val type: FilamentEventType,
    val source: SyncSourceType,
    val deltaGrams: Int,
    val remainingAfterGrams: Int,
    val note: String,
    val externalJobId: String?,
    val createdAt: Long,
)

data class ExportPrintJob(
    val externalJobId: String,
    val source: SyncSourceType,
    val modelName: String,
    val estimatedUsageGrams: Int,
    val targetMaterial: String?,
    val status: PrintJobStatus,
    val note: String,
    val createdAt: Long,
    val confirmedAt: Long?,
)

data class ImportData(
    val rolls: List<ExportRoll>,
    val printJobs: List<ExportPrintJob>,
    val customMaterials: List<String> = emptyList(),
)

data class ImportResult(
    val rollCount: Int,
    val printJobCount: Int,
)

object DataExportImport {
    private const val VERSION = 1
    private const val APP_NAME = "TuoZhuConsumableStatistics"

    fun toExportJson(
        rolls: List<FilamentRollEntity>,
        eventsByRoll: Map<Long, List<FilamentEventEntity>>,
        printJobs: List<PrintJobEntity>,
        customMaterials: List<String> = emptyList(),
    ): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("appName", APP_NAME)
        root.put("exportedAt", System.currentTimeMillis())

        val rollsArray = JSONArray()
        rolls.forEach { roll ->
            val rollJson = JSONObject()
            rollJson.put("brand", roll.brand)
            rollJson.put("name", roll.name)
            rollJson.put("material", roll.material)
            rollJson.put("colorName", roll.colorName)
            rollJson.put("colorHex", roll.colorHex)
            rollJson.put("initialWeightGrams", roll.initialWeightGrams)
            rollJson.put("lowStockThresholdGrams", roll.lowStockThresholdGrams)
            rollJson.put("lastCalibrationWeightGrams", roll.lastCalibrationWeightGrams)
            rollJson.put("lastCalibrationAt", roll.lastCalibrationAt)
            rollJson.put("isActive", roll.isActive)
            rollJson.put("notes", roll.notes)
            rollJson.put("createdAt", roll.createdAt)
            rollJson.put("updatedAt", roll.updatedAt)

            val eventsArray = JSONArray()
            eventsByRoll[roll.id]?.forEach { event ->
                val eventJson = JSONObject()
                eventJson.put("type", event.type.name)
                eventJson.put("source", event.source.name)
                eventJson.put("deltaGrams", event.deltaGrams)
                eventJson.put("remainingAfterGrams", event.remainingAfterGrams)
                eventJson.put("note", event.note)
                if (event.externalJobId != null) {
                    eventJson.put("externalJobId", event.externalJobId)
                }
                eventJson.put("createdAt", event.createdAt)
                eventsArray.put(eventJson)
            }
            rollJson.put("events", eventsArray)
            rollsArray.put(rollJson)
        }
        root.put("rolls", rollsArray)

        val jobsArray = JSONArray()
        printJobs.forEach { job ->
            val jobJson = JSONObject()
            jobJson.put("externalJobId", job.externalJobId)
            jobJson.put("source", job.source.name)
            jobJson.put("modelName", job.modelName)
            jobJson.put("estimatedUsageGrams", job.estimatedUsageGrams)
            if (job.targetMaterial != null) {
                jobJson.put("targetMaterial", job.targetMaterial)
            }
            jobJson.put("status", job.status.name)
            jobJson.put("note", job.note)
            jobJson.put("createdAt", job.createdAt)
            if (job.confirmedAt != null) {
                jobJson.put("confirmedAt", job.confirmedAt)
            }
            jobsArray.put(jobJson)
        }
        root.put("printJobs", jobsArray)

        if (customMaterials.isNotEmpty()) {
            root.put("customMaterials", JSONArray(customMaterials))
        }

        return root.toString(2)
    }

    fun parseImportJson(json: String): ImportData? {
        val root = JSONObject(json)
        if (root.optInt("version", 0) < 1) return null

        val rolls = mutableListOf<ExportRoll>()
        val rollsArray = root.optJSONArray("rolls") ?: return null
        for (i in 0 until rollsArray.length()) {
            val r = rollsArray.getJSONObject(i)
            val eventsArray = r.optJSONArray("events") ?: JSONArray()
            val events = mutableListOf<ExportEvent>()
            for (j in 0 until eventsArray.length()) {
                val e = eventsArray.getJSONObject(j)
                events.add(
                    ExportEvent(
                        type = parseEnum(e.getString("type"), FilamentEventType.CALIBRATION),
                        source = parseEnum(e.optString("source", "MANUAL"), SyncSourceType.MANUAL),
                        deltaGrams = e.getInt("deltaGrams"),
                        remainingAfterGrams = e.getInt("remainingAfterGrams"),
                        note = e.optString("note", ""),
                        externalJobId = if (e.has("externalJobId")) e.getString("externalJobId") else null,
                        createdAt = e.getLong("createdAt"),
                    ),
                )
            }
            rolls.add(
                ExportRoll(
                    brand = r.getString("brand"),
                    name = r.getString("name"),
                    material = r.getString("material"),
                    colorName = r.getString("colorName"),
                    colorHex = r.getString("colorHex"),
                    initialWeightGrams = r.getInt("initialWeightGrams"),
                    lowStockThresholdGrams = r.getInt("lowStockThresholdGrams"),
                    lastCalibrationWeightGrams = r.getInt("lastCalibrationWeightGrams"),
                    lastCalibrationAt = r.getLong("lastCalibrationAt"),
                    isActive = r.getBoolean("isActive"),
                    notes = r.optString("notes", ""),
                    createdAt = r.getLong("createdAt"),
                    updatedAt = r.getLong("updatedAt"),
                    events = events,
                ),
            )
        }

        val printJobs = mutableListOf<ExportPrintJob>()
        val jobsArray = root.optJSONArray("printJobs") ?: JSONArray()
        for (i in 0 until jobsArray.length()) {
            val j = jobsArray.getJSONObject(i)
            printJobs.add(
                ExportPrintJob(
                    externalJobId = j.getString("externalJobId"),
                    source = parseEnum(j.optString("source", "MANUAL"), SyncSourceType.MANUAL),
                    modelName = j.getString("modelName"),
                    estimatedUsageGrams = j.getInt("estimatedUsageGrams"),
                    targetMaterial = if (j.has("targetMaterial")) j.getString("targetMaterial") else null,
                    status = parseEnum(j.getString("status"), PrintJobStatus.CONFIRMED),
                    note = j.optString("note", ""),
                    createdAt = j.getLong("createdAt"),
                    confirmedAt = if (j.has("confirmedAt")) j.getLong("confirmedAt") else null,
                ),
            )
        }

        val customMaterials = mutableListOf<String>()
        val cmArray = root.optJSONArray("customMaterials")
        if (cmArray != null) {
            for (i in 0 until cmArray.length()) {
                customMaterials.add(cmArray.getString(i))
            }
        }

        return ImportData(rolls = rolls, printJobs = printJobs, customMaterials = customMaterials)
    }

    private inline fun <reified T : Enum<T>> parseEnum(name: String, fallback: T): T {
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
