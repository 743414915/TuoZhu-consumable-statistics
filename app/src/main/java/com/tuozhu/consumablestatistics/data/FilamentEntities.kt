package com.tuozhu.consumablestatistics.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "filament_rolls")
data class FilamentRollEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
)

@Entity(
    tableName = "filament_events",
    foreignKeys = [
        ForeignKey(
            entity = FilamentRollEntity::class,
            parentColumns = ["id"],
            childColumns = ["rollId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("rollId")],
)
data class FilamentEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rollId: Long,
    val type: FilamentEventType,
    val source: SyncSourceType,
    val deltaGrams: Int,
    val remainingAfterGrams: Int,
    val note: String,
    val externalJobId: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "print_jobs",
    indices = [Index(value = ["externalJobId"], unique = true), Index("rollId")],
    foreignKeys = [
        ForeignKey(
            entity = FilamentRollEntity::class,
            parentColumns = ["id"],
            childColumns = ["rollId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class PrintJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val externalJobId: String,
    val source: SyncSourceType,
    val modelName: String,
    val estimatedUsageGrams: Int,
    val targetMaterial: String?,
    val status: PrintJobStatus,
    val rollId: Long?,
    val note: String,
    val createdAt: Long,
    val confirmedAt: Long?,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val status: SyncConnectionStatus,
    val lastSyncSource: SyncSourceType,
    val lastSyncAt: Long?,
    val lastMessage: String,
)

enum class FilamentEventType {
    CALIBRATION,
    PRINT_USAGE,
    MANUAL_ADJUSTMENT,
}

enum class PrintJobStatus {
    DRAFT,
    CONFIRMED,
}

enum class SyncSourceType {
    MANUAL,
    DESKTOP_AGENT,
    CLOUD,
}

enum class SyncConnectionStatus {
    IDLE,
    SUCCESS,
    OFFLINE,
    ERROR,
}

@Entity(tableName = "custom_materials")
data class CustomMaterialEntity(
    @PrimaryKey
    val name: String,
    val createdAt: Long,
)

const val ARCHIVED_ROLL_NOTE_PREFIX = "[[ARCHIVED]]"

fun FilamentRollEntity.isArchivedRoll(): Boolean = notes.startsWith(ARCHIVED_ROLL_NOTE_PREFIX)
