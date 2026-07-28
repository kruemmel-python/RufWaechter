package de.kruemmel.rufwaechter.reputation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.kruemmel.rufwaechter.RufWaechterApplication
import de.kruemmel.rufwaechter.phoneblock.PhoneBlockSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class PhoneBlockWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RufWaechterApplication).container
        if (!container.settingsRepository.settings.first().phoneBlockEnabled) return Result.success()
        return when (val result = withContext(Dispatchers.IO) { container.phoneBlockSynchronizer.synchronize() }) {
            is PhoneBlockSyncResult.Failed -> if (result.retryable) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }
}
