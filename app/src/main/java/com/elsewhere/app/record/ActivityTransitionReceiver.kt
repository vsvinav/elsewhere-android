package com.elsewhere.app.record

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What Activity Recognition last said the phone was doing, shared between the receiver and the service. */
@Singleton
class ActivityHints @Inject constructor() {
    val current = MutableStateFlow<String?>(null)
    val still: StateFlow<Boolean> get() = stillFlow
    private val stillFlow = MutableStateFlow(false)

    fun apply(type: Int, entered: Boolean) {
        val name = nameOf(type)
        if (entered) { current.value = name; stillFlow.value = type == DetectedActivity.STILL }
        else if (current.value == name) { current.value = null; if (type == DetectedActivity.STILL) stillFlow.value = false }
    }

    companion object {
        fun nameOf(type: Int) = when (type) {
            DetectedActivity.STILL -> "STILL"; DetectedActivity.WALKING -> "WALKING"; DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"; DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"; DetectedActivity.ON_FOOT -> "ON_FOOT"
            else -> "UNKNOWN"
        }
    }
}

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var hints: ActivityHints

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        for (e in result.transitionEvents) hints.apply(e.activityType, e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER)
    }

    companion object {
        const val ACTION = "com.elsewhere.app.record.ACTIVITY_TRANSITION"
        fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, ActivityTransitionReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
