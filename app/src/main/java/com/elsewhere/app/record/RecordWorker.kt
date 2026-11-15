package com.elsewhere.app.record

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Every six hours: close a stale stay, prune raw fixes, and restart the service if the system dropped it. */
@HiltWorker
class RecordWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val recorder: Recorder, private val controller: RecordController,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        recorder.sweep(System.currentTimeMillis() / 1000)
        controller.resumeIfEnabled()
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "record-sweep"
        private const val RESUME = "record-resume"

        fun schedule(ctx: Context) = WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RecordWorker>(6, TimeUnit.HOURS).build(),
        )

        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC); WorkManager.getInstance(ctx).cancelUniqueWork(RESUME) }

        /** One shot after a pause so recording comes back without waiting for the next sweep. */
        fun resumeAt(ctx: Context, delaySeconds: Long) = WorkManager.getInstance(ctx).enqueueUniqueWork(
            RESUME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecordWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
        )
    }
}
