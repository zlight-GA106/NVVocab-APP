package com.zlight106.nvvocab.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zlight106.nvvocab.NvvocabApplication
import com.zlight106.nvvocab.data.SyncMode
import com.zlight106.nvvocab.data.SyncSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncRuntimeStatus {
    IDLE,
    RUNNING,
    SUCCESS,
    FAILED,
}

data class SyncRuntimeState(
    val status: SyncRuntimeStatus = SyncRuntimeStatus.IDLE,
    val updatedAt: Long? = null,
)

object SyncStateMonitor {
    private val mutableState = MutableStateFlow(SyncRuntimeState())
    val state: StateFlow<SyncRuntimeState> = mutableState.asStateFlow()

    fun update(status: SyncRuntimeStatus) {
        mutableState.value = SyncRuntimeState(status = status, updatedAt = System.currentTimeMillis())
    }
}

class SyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as NvvocabApplication
        if (application.preferences.readSession() == null) {
            SyncStateMonitor.update(SyncRuntimeStatus.IDLE)
            return Result.success()
        }
        SyncStateMonitor.update(SyncRuntimeStatus.RUNNING)
        return runCatching { application.repository.synchronize() }
            .fold(
                onSuccess = {
                    SyncStateMonitor.update(SyncRuntimeStatus.SUCCESS)
                    Result.success()
                },
                onFailure = {
                    SyncStateMonitor.update(SyncRuntimeStatus.FAILED)
                    Result.retry()
                },
            )
    }
}

object SyncScheduler {
    private const val CHANGE_SYNC = "nvvocab_change_sync"
    private const val PERIODIC_SYNC = "nvvocab_periodic_sync"
    private const val SYNC_TAG = "nvvocab_sync"

    private val connectedConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun configure(context: Context, settings: SyncSettings) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(CHANGE_SYNC)
        if (!settings.enabled) {
            manager.cancelUniqueWork(PERIODIC_SYNC)
            return
        }
        if (settings.mode == SyncMode.ON_LOCAL_CHANGE) {
            manager.cancelUniqueWork(PERIODIC_SYNC)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            settings.intervalMinutes.coerceIn(15L, 1_440L),
            TimeUnit.MINUTES,
        )
            .setConstraints(connectedConstraint)
            .addTag(SYNC_TAG)
            .build()
        manager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runAfterLocalChange(context: Context, settings: SyncSettings) {
        if (!settings.enabled || settings.mode != SyncMode.ON_LOCAL_CHANGE) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setConstraints(connectedConstraint)
            .addTag(SYNC_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHANGE_SYNC,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(connectedConstraint)
            .addTag(SYNC_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHANGE_SYNC,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
