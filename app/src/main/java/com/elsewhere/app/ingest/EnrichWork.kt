package com.elsewhere.app.ingest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.RecordSource
import com.elsewhere.app.data.Settings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** What the running enrichment is up to, for the stamp in the corner; `error` is the last refusal's message. */
@Singleton
class EnrichState @Inject constructor() {
    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    fun report(p: Progress?) { _progress.value = p }
    fun fail(message: String?) { _error.value = message }
}

/** The one record-and-key combination a run is for; a new one replaces whatever is queued. */
data class EnrichJob(val sha: String, val key: String)

/** Null when nothing should run: no record yet, no key, or a key Places refused at check time. */
fun enrichJob(sha: String?, s: Settings): EnrichJob? =
    if (sha == null || s.mapsKey.isBlank() || !s.keyPlaces) null else EnrichJob(sha, s.mapsKey)

/** Names the busiest places in the background; one run per record-and-key, replaced when either changes. */
@HiltWorker
class EnrichWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters,
    private val enricher: Enricher, private val source: RecordSource, private val state: EnrichState,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY) ?: return Result.failure()
        val record = withTimeoutOrNull(30_000) { source.record.filterNotNull().first() } ?: return Result.retry()
        state.fail(null)
        return try {
            enricher.run(record.places, key) { state.report(it) }
            Result.success()
        } catch (e: PlacesError) {
            state.fail(e.message); Result.failure()
        } catch (e: CancellationException) {
            throw e // WorkManager stopped us; it decides what happens next, not a retry here
        } catch (e: Exception) {
            state.report(null); if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val NAME = "enrich"
        const val KEY = "key"
        fun enqueue(wm: WorkManager, job: EnrichJob) {
            wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<EnrichWorker>().setInputData(workDataOf(KEY to job.key)).build())
        }
    }
}

/** Watches the record and the key and queues a run whenever the pair changes. */
@Singleton
class EnrichScheduler @Inject constructor(private val source: RecordSource, private val maps: MapsSettings, private val scope: CoroutineScope) {
    fun start(wm: WorkManager) {
        combine(source.record, maps.settings) { r, s -> enrichJob(r?.meta?.sourceSha256, s) }
            .distinctUntilChanged().filterNotNull()
            .onEach { EnrichWorker.enqueue(wm, it) }
            .launchIn(scope)
    }
}
