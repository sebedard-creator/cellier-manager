package com.cellier.manager

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.cellier.manager.data.CellarDatabase
import com.cellier.manager.work.IndexingWorker
import com.cellier.manager.work.CompanionSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.cellier.manager.util.PhotoCapture
import com.cellier.manager.repository.CellarRepository

/**
 * Application class. Configure WorkManager pour le logging.
 * Référencée dans AndroidManifest via android:name=".CellarApplication"
 */
class CellarApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        IndexingWorker.cancelLegacyWork(this)
        CompanionSyncWorker.schedule(this)
        applicationScope.launch {
            val database = CellarDatabase.getInstance(this@CellarApplication)
            val dao = database.cellarDao()
            dao.neutralizeLegacyIndexingState()
            CellarRepository.getInstance(this@CellarApplication).backfillCanonicalWebIdentities()
            database.enrichmentDao().backfillAppliedVivinoMatches()
            database.enrichmentDao().backfillAppliedUntappdMatches()
            PhotoCapture.cleanupOrphans(
                this@CellarApplication,
                dao.getAllSnapshot().map { it.photoPath }.filter(String::isNotBlank).toSet()
            )
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
