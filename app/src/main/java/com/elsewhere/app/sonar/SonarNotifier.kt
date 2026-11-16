package com.elsewhere.app.sonar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elsewhere.app.MainActivity
import com.elsewhere.app.R
import com.elsewhere.app.crew.Friend
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a notification tap or a deep link wants the app to land; taken by the shell on the next frame. */
object PendingCrew {
    /** A pair code to prefill on the Crew screen. */
    val code = MutableStateFlow<String?>(null)
    /** Open the Map with the Friends layer on. */
    val map = MutableStateFlow(false)

    fun take(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_MAP, false)) map.value = true
        com.elsewhere.app.crew.ShareCode.fromLink(intent.dataString)?.let { code.value = it }
    }

    const val EXTRA_MAP = "crew_map"
}

/** The Sonar notification: high importance, its own sound and buzz, and deliberately vague about place. */
class SonarNotifier(private val ctx: Context) : SonarAlerts {
    override fun nearby(friend: Friend, test: Boolean, sent: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel(ctx))
        val open = PendingIntent.getActivity(
            ctx, ID, Intent(ctx, MainActivity::class.java).putExtra(PendingCrew.EXTRA_MAP, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mute = PendingIntent.getBroadcast(
            ctx, ID + 1, Intent(ctx, SonarMuteReceiver::class.java).putExtra(SonarMuteReceiver.EXTRA_DEVICE, friend.deviceId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_record_pin)
            .setContentTitle(if (test) testTitle(friend.name, sent) else title(friend.name)).setContentText(if (test) TEST_BODY else BODY)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (test) TEST_BODY else BODY))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setSound(soundUri(ctx)).setVibrate(VIBRATION)
            .setContentIntent(open).setAutoCancel(true)
            .addAction(0, "Open Crew Map", open)
            .addAction(0, "Mute Sonar for today", mute)
            .build()
        nm.notify(ID, n)
    }

    companion object {
        const val CHANNEL = "sonar"
        const val ID = 47
        const val BODY = "Within a mile of you, and neither of you is usually around here. Say hi?"
        val VIBRATION = longArrayOf(0, 40, 120, 60)

        const val TEST_BODY = "Fired on purpose to check Sonar works. Nothing is recorded."
        fun title(name: String) = "Sonar · $name is nearby"
        fun testTitle(name: String, sent: Boolean) = if (sent) "Sonar · Test ping to $name" else "Sonar · Test ping from $name"
        fun soundUri(ctx: Context): Uri = Uri.parse("android.resource://${ctx.packageName}/raw/sonar")

        fun channel(ctx: Context) = NotificationChannel(CHANNEL, "Sonar", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A friend with Sonar on is within a mile, somewhere neither of you usually is."
            setSound(soundUri(ctx), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); vibrationPattern = VIBRATION
            enableLights(false)
        }
    }
}

/** "Mute Sonar for today" from the notification. */
@AndroidEntryPoint
class SonarMuteReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: SonarEngine
    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_DEVICE) ?: return
        context.getSystemService(NotificationManager::class.java).cancel(SonarNotifier.ID)
        val result = goAsync()
        scope.launch { try { engine.muteToday(id) } finally { result.finish() } }
    }

    companion object { const val EXTRA_DEVICE = "device" }
}

/** Plays the in-app ping through a static AudioTrack built from [SonarTone]; same maths as the asset. */
class SonarTonePlayer(private val ctx: Context) {
    private var pcm: ShortArray? = null

    fun play() {
        val data = pcm ?: SonarTone.pcm16().also { pcm = it }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SonarTone.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        track.write(data, 0, data.size)
        track.setNotificationMarkerPosition(data.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.release() }
            override fun onPeriodicNotification(t: AudioTrack) = Unit
        })
        track.play()
    }

    fun ringerAllows(): Boolean = (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode == AudioManager.RINGER_MODE_NORMAL
}
