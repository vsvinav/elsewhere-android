package com.elsewhere.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** A round when the network is back after a failed one; WorkManager spaces the attempts out. */
@HiltWorker
class SyncWorker @AssistedInject constructor(@Assisted ctx: Context, @Assisted params: WorkerParameters, private val engine: SyncEngine) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        engine.run(force = true)
        return if (engine.status.value is SyncStatus.Error && (engine.status.value as SyncStatus.Error).kind != SyncError.Locked) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK = "sync-retry"
        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }
}
