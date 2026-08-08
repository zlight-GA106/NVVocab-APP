package com.zlight106.nvvocab.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailyWidgetResetWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        DailyMemoWidgetUpdater.updateAll(applicationContext)
        DailyWidgetResetScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}

object DailyWidgetResetScheduler {
    private const val UNIQUE_WORK = "nvvocab_daily_widget_midnight_reset"

    fun scheduleNext(context: Context) {
        val now = ZonedDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1)
            .atStartOfDay(now.zone)
            .plusSeconds(2)
        val delayMillis = Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<DailyWidgetResetWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
