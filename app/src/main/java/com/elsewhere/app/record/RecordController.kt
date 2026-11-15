package com.elsewhere.app.record

import android.content.Context
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** The recorder as the screen sees it; `RecordController` is the real one, tests use a fake. */
interface RecordControl {
    val recording: Flow<Recording>
    suspend fun start()
    suspend fun stop()
    suspend fun pause(seconds: Long = 24 * 3600)
    suspend fun stats(): RecordStats
}

/** Start, stop and pause recording: the settings are the truth, the service and worker follow them. */
@Singleton
class RecordController @Inject constructor(
    @ApplicationContext private val ctx: Context, private val settings: SettingsStore, private val recorder: Recorder,
) : RecordControl {
    override val recording: Flow<Recording> get() = settings.recording

    override suspend fun stats() = recorder.stats()

    override suspend fun start() {
        val cur = settings.recording.first()
        val now = System.currentTimeMillis() / 1000
        settings.saveRecording(Recording(enabled = true, since = if (cur.since > 0) cur.since else now, pausedUntil = 0L))
        RecordService.start(ctx)
        RecordWorker.schedule(ctx)
    }

    override suspend fun stop() {
        settings.saveRecording(Recording(enabled = false, since = 0L, pausedUntil = 0L))
        RecordService.stop(ctx)
        RecordWorker.cancel(ctx)
    }

    override suspend fun pause(seconds: Long) {
        val cur = settings.recording.first()
        settings.saveRecording(cur.copy(pausedUntil = System.currentTimeMillis() / 1000 + seconds))
        RecordService.stop(ctx)
        RecordWorker.resumeAt(ctx, seconds)
    }

    /** After a boot, a sweep or a pause: bring the service back if the settings say so. */
    suspend fun resumeIfEnabled() {
        val cur = settings.recording.first()
        if (cur.active(System.currentTimeMillis() / 1000)) RecordService.start(ctx)
    }
}
