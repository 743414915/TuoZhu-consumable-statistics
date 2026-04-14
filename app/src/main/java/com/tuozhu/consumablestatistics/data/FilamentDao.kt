package com.tuozhu.consumablestatistics.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
@TypeConverters(FilamentTypeConverters::class)
interface FilamentDao {
    @Query("SELECT * FROM filament_rolls ORDER BY isActive DESC, updatedAt DESC, id DESC")
    fun observeRolls(): Flow<List<FilamentRollEntity>>

    @Query("SELECT * FROM filament_events ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 20): Flow<List<FilamentEventEntity>>

    @Query("SELECT * FROM print_jobs WHERE status = 'DRAFT' ORDER BY createdAt DESC, id DESC")
    fun observePendingPrintJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE status = 'CONFIRMED' ORDER BY confirmedAt DESC, createdAt DESC, id DESC LIMIT :limit")
    fun observePrintHistory(limit: Int = 30): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs WHERE externalJobId = :externalJobId LIMIT 1")
    suspend fun getPrintJobByExternalId(externalJobId: String): PrintJobEntity?

    @Query("SELECT * FROM print_jobs WHERE id = :jobId LIMIT 1")
    suspend fun getPrintJobById(jobId: Long): PrintJobEntity?

    @Query("SELECT * FROM print_jobs WHERE status = 'DRAFT'")
    suspend fun getDraftPrintJobs(): List<PrintJobEntity>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observeSyncState(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM filament_rolls WHERE id = :rollId LIMIT 1")
    suspend fun getRollById(rollId: Long): FilamentRollEntity?

    @Query("SELECT * FROM filament_rolls WHERE id != :excludedRollId ORDER BY updatedAt DESC, id DESC LIMIT 1")
    suspend fun getNextActiveCandidate(excludedRollId: Long): FilamentRollEntity?

    @Query("SELECT COUNT(*) FROM filament_rolls WHERE isActive = 1")
    suspend fun getActiveRollCount(): Int

    @Query("SELECT * FROM filament_rolls WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveRoll(): FilamentRollEntity?

    @Query("SELECT COALESCE(SUM(deltaGrams), 0) FROM filament_events WHERE rollId = :rollId AND createdAt > :afterTime")
    suspend fun sumDeltaAfter(rollId: Long, afterTime: Long): Int

    @Query("UPDATE filament_rolls SET isActive = CASE WHEN id = :rollId THEN 1 ELSE 0 END, updatedAt = :updatedAt")
    suspend fun setActiveRollInternal(rollId: Long, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoll(roll: FilamentRollEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: FilamentEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrintJob(job: PrintJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Update
    suspend fun updateRoll(roll: FilamentRollEntity)

    @Delete
    suspend fun deleteRoll(roll: FilamentRollEntity)

    @Update
    suspend fun updatePrintJob(job: PrintJobEntity)

    @Query("DELETE FROM print_jobs WHERE status = 'DRAFT'")
    suspend fun deleteAllDraftPrintJobs()

    @Query("DELETE FROM print_jobs WHERE status = 'DRAFT' AND externalJobId NOT IN (:externalJobIds)")
    suspend fun deleteDraftPrintJobsNotIn(externalJobIds: List<String>)

    @Transaction
    suspend fun updateRollAndInsertEvent(roll: FilamentRollEntity, event: FilamentEventEntity) {
        updateRoll(roll)
        insertEvent(event)
    }
}
