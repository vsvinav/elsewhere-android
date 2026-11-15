package com.elsewhere.app.record

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
import com.elsewhere.app.MainActivity
import com.elsewhere.app.R
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var fused: FusedLocationProviderClient
    private var still = false
    private var lastFixTs: Long? = null

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification())
        startListening()
        return START_STICKY
    }

    @Suppress("MissingPermission")
    private fun startListening() {
        request(still)
        runCatching {
            val types = listOf(DetectedActivity.STILL, DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_BICYCLE, DetectedActivity.IN_VEHICLE)
            val transitions = types.flatMap { t ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .map { ActivityTransition.Builder().setActivityType(t).setActivityTransition(it).build() }
            }
            ActivityRecognition.getClient(this).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), ActivityTransitionReceiver.pendingIntent(this))
        }
        scope.launch {
            hints.still.collectLatest { s ->
                if (s == still) return@collectLatest
                still = s
                request(s)
                // Sitting down: note the spot now, so a stay confirms even if no fix arrives for a while.
                if (s) runCatching { fused.lastLocation.addOnSuccessListener { l -> if (l != null) callback.onLocationResult(LocationResult.create(listOf(l))) } }
            }
        }
    }

    /** Balanced power while moving; a slow, undistanced low-power request while still so stays keep getting fixes. */
    @Suppress("MissingPermission")
    private fun request(still: Boolean) {
        fused.removeLocationUpdates(callback)
        val req = if (still) {
            LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 5 * 60_000L).setMinUpdateIntervalMillis(2 * 60_000L).build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L).setMinUpdateIntervalMillis(20_000L).setMinUpdateDistanceMeters(30f).build()
        }
        runCatching { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        runCatching { ActivityRecognition.getClient(this).removeActivityTransitionUpdates(ActivityTransitionReceiver.pendingIntent(this)) }
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
            .setContentTitle(notificationText(lastFixTs, System.currentTimeMillis() / 1000))
            .setContentText("Stays on this phone. Nothing is uploaded.")
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

        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, RecordService::class.java))
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, RecordService::class.java).setAction(ACTION_STOP))
    }
}
