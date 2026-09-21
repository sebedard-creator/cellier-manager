package com.cellier.manager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Adaptateur inoffensif pour d'anciens WorkRequest sérialisés. */
class IndexingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val LEGACY_WORK_NAME = "cellar_indexing"
        fun cancelLegacyWork(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
        }
    }
}
