package com.tuozhu.consumablestatistics.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FilamentRollEntity::class,
        FilamentEventEntity::class,
        PrintJobEntity::class,
        SyncStateEntity::class,
        CustomMaterialEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(FilamentTypeConverters::class)
abstract class ConsumableDatabase : RoomDatabase() {
    abstract fun filamentDao(): FilamentDao

    companion object {
        @Volatile
        private var instance: ConsumableDatabase? = null

        fun getInstance(context: Context): ConsumableDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ConsumableDatabase::class.java,
                    "consumable_manager.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
