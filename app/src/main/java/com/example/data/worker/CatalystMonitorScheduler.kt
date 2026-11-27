package com.example.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.domain.engine.BackgroundMonitorConfig
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling, updating, cancelling, and on-demand execution of the
 * periodic [CatalystMonitorWorker] via Jetpack WorkManager.
 */
object CatalystMonitorScheduler {

    const val UNIQUE_PERIODIC_WORK_NAME = "market_scan_catalyst_monitor_periodic"
    const val UNIQUE_IMMEDIATE_WORK_NAME = "market_scan_catalyst_monitor_now"
    const val WORK_TAG = "catalyst_market_monitor"

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(false)
        .build()

    fun syncSchedule(context: Context, config: BackgroundMonitorConfig) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (!config.enabled) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            return
        }

        val intervalMin = config.intervalMinutes.toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<CatalystMonitorWorker>(
            repeatInterval = intervalMin,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runImmediateCheck(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val request = OneTimeWorkRequestBuilder<CatalystMonitorWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(CatalystMonitorWorker.KEY_MANUAL_TRIGGER to true))
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Ensures periodic background checks remain scheduled whenever the user has armed
     * any Price Threshold or AI Signal Change push alerts, even if the separate news
     * catalyst monitor toggle is off.
     */
    fun ensureScheduledForAlerts(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val request = PeriodicWorkRequestBuilder<CatalystMonitorWorker>(
            repeatInterval = 15L,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
