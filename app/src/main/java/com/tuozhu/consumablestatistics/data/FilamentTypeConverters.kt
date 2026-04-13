package com.tuozhu.consumablestatistics.data

import androidx.room.TypeConverter

class FilamentTypeConverters {
    @TypeConverter
    fun fromEventType(value: FilamentEventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): FilamentEventType = FilamentEventType.valueOf(value)

    @TypeConverter
    fun fromPrintJobStatus(value: PrintJobStatus): String = value.name

    @TypeConverter
    fun toPrintJobStatus(value: String): PrintJobStatus = PrintJobStatus.valueOf(value)

    @TypeConverter
    fun fromSyncSource(value: SyncSourceType): String = value.name

    @TypeConverter
    fun toSyncSource(value: String): SyncSourceType = SyncSourceType.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncConnectionStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncConnectionStatus = SyncConnectionStatus.valueOf(value)
}
