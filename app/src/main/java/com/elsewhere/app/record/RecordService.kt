package com.elsewhere.app.record

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.elsewhere.app.MainActivity
import com.elsewhere.app.R
import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.SettingsStore
import com.elsewhere.app.ingest.record.Fix
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The foreground service that listens for location while recording is on. All judgement lives in
 * `Recorder`/`Segmenter`; this class only wires the platform callbacks to it.
 */
@AndroidEntryPoint
class RecordService : Service() {
    @Inject lateinit var recorder: Recorder
    @Inject lateinit var hints: ActivityHints
    @Inject lateinit var gate: PermissionGate
    @Inject lateinit var settings: SettingsStore

    // One lane: fixes must reach the segmenter in the order they arrived.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private lateinit var fused: FusedLocationProviderClient
    private var still = false
    private var paused = false
    private var lastFixTs: Long? = null
    private var stillJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val battery = (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            for (l in result.locations) {
                val ts = l.time / 1000
                val fix = Fix(ts, l.latitude, l.longitude, l.accuracy, if (l.hasSpeed()) l.speed else null, hints.current.value)
                lastFixTs = ts
                scope.launch { recorder.onFix(fix, if (l.hasAltitude()) l.altitude else null, battery?.takeIf { it in 0..100 }) }
            }
            notify(notification())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        channel()
        getSystemService(NotificationManager::class.java).cancel(NUDGE_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!gate.granted(Manifest.permission.ACCESS_FINE_LOCATION)) return standDown()
        // A redelivery after the system killed us carries no action: the settings know whether we were paused.
        paused = pausedFor(intent?.action, if (intent == null) runBlocking { settings.recording.first() } else Recording(), System.currentTimeMillis() / 1000)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification())
        if (paused) stopListening() else startListening()
        return START_STICKY
    }

    /**
     * Location was revoked while we were off. A location foreground service would throw, and so would
     * stopping before any `startForeground` after a `startForegroundService` launch: go foreground as a
     * short service for a moment, switch recording off for good, then leave.
     */
    private fun standDown(): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type) }
        runBlocking { withContext(NonCancellable) { settings.saveRecording(Recording()) } }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    private fun stopListening() {
        stillJob?.cancel(); stillJob = null
        fused.removeLocationUpdates(callback)
        runCatching { ActivityRecognition.getClient(this).removeActivityTransitionUpdates(ActivityTransitionReceiver.pendingIntent(this)) }
    }

    @Suppress("MissingPermission")
    private fun startListening() {
        // Every start intent lands here; only one set of listeners may be live.
        stillJob?.cancel()
        request(still)
        runCatching {
            val types = listOf(DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE)
            val transitions = types.flatMap { t ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .map { ActivityTransition.Builder().setActivityType(t).setActivityTransition(it).build() }
            }
            ActivityRecognition.getClient(this).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), ActivityTransitionReceiver.pendingIntent(this))
        }
        stillJob = scope.launch {
            hints.still.collectLatest { s ->
                if (s == still) return@collectLatest
                still = s
                request(s)
                // Sitting down: note the spot now, so a stay confirms even if no fix arrives for a while.
                if (s) runCatching { fused.lastLocation.addOnSuccessListener { l -> if (l != null) callback.onLocationResult(LocationResult.create(listOf(l))) } }
            }
        }
    }

    /**
     * Balanced power throughout: low power would hand back kilometre-wide fixes the segmenter can't place.
     * While still the request is slower and undistanced, so a stay keeps receiving heartbeats.
     */
    @Suppress("MissingPermission")
    private fun request(still: Boolean) {
        fused.removeLocationUpdates(callback)
        val req = if (still) {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 3 * 60_000L).setMinUpdateIntervalMillis(90_000L).build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L).setMinUpdateIntervalMillis(20_000L).setMinUpdateDistanceMeters(30f).build()
        }
        runCatching { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) }
    }

    override fun onDestroy() {
        stopListening()
        scope.cancel()
        super.onDestroy()
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Elsewhere records your location on this phone"
            setShowBadge(false)
        })
    }

    private fun notify(n: Notification) = getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_record_pin)
            .setContentTitle(if (paused) "Elsewhere is paused" else notificationText(lastFixTs, System.currentTimeMillis() / 1000))
            .setContentText(if (paused) "Recording resumes by itself. Nothing is uploaded." else "Stays on this phone. Nothing is uploaded.")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true).setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL = "recording"
        const val NOTIFICATION_ID = 41
        const val ACTION_STOP = "com.elsewhere.app.record.STOP"
        const val ACTION_PAUSE = "com.elsewhere.app.record.PAUSE"
        const val NUDGE_ID = 42

        private fun intent(ctx: Context) = Intent(ctx, RecordService::class.java)

        /**
         * From the background (a worker, a receiver) Android 12+ may refuse to start a foreground service.
         * Then the one legal path left is the user's tap: leave a notification whose tap starts it.
         */
        fun start(ctx: Context, gate: PermissionGate) {
            if (!gate.granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                ctx.getSystemService(NotificationManager::class.java).cancel(NUDGE_ID)
                return
            }
            val ok = runCatching { ctx.startForegroundService(intent(ctx)) }.isSuccess
            if (!ok) nudge(ctx)
        }

        fun stop(ctx: Context) { runCatching { ctx.startService(intent(ctx).setAction(ACTION_STOP)) } }
        fun pause(ctx: Context) { runCatching { ctx.startForegroundService(intent(ctx).setAction(ACTION_PAUSE)) } }

        private fun nudge(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
            val resume = PendingIntent.getForegroundService(ctx, 2, intent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(NUDGE_ID, NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_record_pin)
                .setContentTitle("Recording paused — tap to resume")
                .setContentText("Android would not let Elsewhere restart on its own.")
                .setContentIntent(resume).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build())
        }
    }
}
