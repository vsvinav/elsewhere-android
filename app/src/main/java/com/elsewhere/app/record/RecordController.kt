package com.elsewhere.app.record

import android.Manifest
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

/** The platform side of recording (service and worker), behind a seam so the controller is testable. */
interface ServiceLink {
    fun start()
    fun stop()
    fun pause()
    fun schedule()
    fun cancelWork()
    fun resumeAt(seconds: Long)
}

class AndroidServiceLink @Inject constructor(@ApplicationContext private val ctx: Context) : ServiceLink {
    override fun start() = RecordService.start(ctx)
    override fun stop() = RecordService.stop(ctx)
    override fun pause() = RecordService.pause(ctx)
    override fun schedule() { RecordWorker.schedule(ctx) }
    override fun cancelWork() = RecordWorker.cancel(ctx)
    override fun resumeAt(seconds: Long) { RecordWorker.resumeAt(ctx, seconds) }
}

/** Start, stop and pause recording: the settings are the truth, the service and worker follow them. */
@Singleton
class RecordController @Inject constructor(
    private val settings: SettingsStore, private val recorder: Recorder, private val gate: PermissionGate, private val link: ServiceLink,
) : RecordControl {
    override val recording: Flow<Recording> get() = settings.recording

    override suspend fun stats() = recorder.stats()

    override suspend fun start() {
        val cur = settings.recording.first()
        val now = System.currentTimeMillis() / 1000
        settings.saveRecording(Recording(enabled = true, since = if (cur.since > 0) cur.since else now, pausedUntil = 0L))
        link.start()
        link.schedule()
    }

    override suspend fun stop() {
        settings.saveRecording(Recording(enabled = false, since = 0L, pausedUntil = 0L))
        // Whatever stay is open is real: write it before the segmenter is forgotten.
        recorder.close()
        link.stop()
        link.cancelWork()
    }

    /** The service stays up but idle, so coming back is a plain start from a foreground process. */
    override suspend fun pause(seconds: Long) {
        val cur = settings.recording.first()
        settings.saveRecording(cur.copy(pausedUntil = System.currentTimeMillis() / 1000 + seconds))
        link.pause()
        link.resumeAt(seconds)
    }

    /** After a boot, a sweep or a pause: bring the service back if the settings say so. */
    suspend fun resumeIfEnabled() {
        val cur = settings.recording.first()
        if (!cur.enabled) return
        if (!gate.granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Location was taken away while we were off; a foreground location service would crash.
            settings.saveRecording(Recording())
            return
        }
        if (cur.active(System.currentTimeMillis() / 1000)) link.start()
    }
}
