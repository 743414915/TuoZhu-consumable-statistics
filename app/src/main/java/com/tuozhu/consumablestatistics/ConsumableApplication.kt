package com.tuozhu.consumablestatistics

import android.app.Application
import com.tuozhu.consumablestatistics.data.ConsumableDatabase
import com.tuozhu.consumablestatistics.data.FilamentRepository
import com.tuozhu.consumablestatistics.sync.DesktopAgentSyncCoordinator
import com.tuozhu.consumablestatistics.sync.SyncSettingsStore

class ConsumableApplication : Application() {
    lateinit var repository: FilamentRepository
        private set
    lateinit var syncSettingsStore: SyncSettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        val database = ConsumableDatabase.getInstance(this)
        syncSettingsStore = SyncSettingsStore(this)
        repository = FilamentRepository(
            dao = database.filamentDao(),
            database = database,
            syncCoordinator = DesktopAgentSyncCoordinator(syncSettingsStore),
        )
    }
}
