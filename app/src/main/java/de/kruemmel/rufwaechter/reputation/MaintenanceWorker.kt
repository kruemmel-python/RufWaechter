package de.kruemmel.rufwaechter.reputation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.kruemmel.rufwaechter.RufWaechterApplication
import kotlinx.coroutines.flow.first

class MaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RufWaechterApplication).container
        val settings = container.settingsRepository.settings.first()
        return runCatching {
            container.repository.performMaintenance(settings.historyRetentionDays)
            if (settings.onlineUpdatesEnabled && settings.feedUrl.isNotBlank()) {
                when (HttpsJsonFeedProvider(settings.feedUrl, container.importExportManager).refresh()) {
                    is ReputationRefreshResult.Failed -> return Result.retry()
                    else -> Unit
                }
            }
            container.rebuildSnapshot()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
