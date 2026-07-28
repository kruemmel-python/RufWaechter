package de.kruemmel.rufwaechter.reputation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.kruemmel.rufwaechter.domain.ScreeningSettings
import java.util.concurrent.TimeUnit

class WorkScheduler(private val context: Context) {
    fun apply(settings: ScreeningSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.onlineUpdatesEnabled) {
            manager.cancelUniqueWork(WORK_NAME)
        } else {
            val networkType = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(
                settings.updateIntervalHours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
        applyPhoneBlock(settings)
    }

    fun requestPhoneBlockNow(wifiOnly: Boolean) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<PhoneBlockWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PHONEBLOCK_NOW_WORK_NAME,
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun applyPhoneBlock(settings: ScreeningSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.phoneBlockEnabled) {
            manager.cancelUniqueWork(PHONEBLOCK_WORK_NAME)
            manager.cancelUniqueWork(PHONEBLOCK_NOW_WORK_NAME)
            return
        }
        val networkType = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<PhoneBlockWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(
            PHONEBLOCK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        private const val WORK_NAME = "rufwaechter-maintenance"
        private const val PHONEBLOCK_WORK_NAME = "rufwaechter-phoneblock-sync"
        private const val PHONEBLOCK_NOW_WORK_NAME = "rufwaechter-phoneblock-now"
    }
}
