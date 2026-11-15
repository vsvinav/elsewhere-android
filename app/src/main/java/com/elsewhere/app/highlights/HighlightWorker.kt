package com.elsewhere.app.highlights

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.elsewhere.app.MainActivity
import com.elsewhere.app.R
import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.HighlightState
import com.elsewhere.app.analytics.highlightState
import com.elsewhere.app.analytics.localToday
import com.elsewhere.app.data.RecordSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Once a day at the user's hour: print today's highlight as a silent notification, or nothing on a quiet day. */
@HiltWorker
class HighlightWorker @AssistedInject constructor(
    @Assisted ctx: Context, @Assisted params: WorkerParameters, private val source: RecordSource,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val record = withTimeoutOrNull(60_000) { source.record.filterNotNull().first() } ?: return Result.retry()
        val today = localToday()
        val state = highlightState(record, today)
        if (shouldNotify(state)) show(applicationContext, (state as HighlightState.Found).highlight, today)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "highlights"
        const val EXTRA_OPEN = "open_highlight"
        private const val WORK = "highlight-daily"
        private const val ID = 7

        /** Every 24 hours from the next `minuteOfDay`. Re-enqueued from scratch: UPDATE would keep the old period's clock, so a new hour would wait a day. */
        fun schedule(ctx: Context, minuteOfDay: Int, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) =
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                PeriodicWorkRequestBuilder<HighlightWorker>(1, TimeUnit.DAYS).setInitialDelay(nextRunDelayMs(nowMs, minuteOfDay, zone), TimeUnit.MILLISECONDS).build(),
            )

        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(WORK) }

        fun show(ctx: Context, h: Highlight, today: String) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "On this day", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null); enableVibration(false) })
            val open = PendingIntent.getActivity(
                ctx, ID, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, today).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val title = notificationTitle(h)
            val picture = HighlightPicture.draw(ctx, title, notificationBody(h), h.date.substring(0, 4))
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_record_pin).setContentTitle(title).setContentText(notificationBody(h))
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(picture).bigLargeIcon(null as Bitmap?))
                .setSilent(true).setContentIntent(open).setAutoCancel(true).build()
            nm.notify(ID, n)
        }
    }
}

/**
 * The card as a picture: stock sheet, ink rule and headline, a red year. Drawn with Canvas rather than
 * composed, since a worker has no window to compose into.
 */
object HighlightPicture {
    fun draw(ctx: Context, title: String, body: String, year: String, w: Int = 1080, h: Int = 540): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFEFE6D2.toInt())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF141414.toInt() }
        c.drawRect(0f, 0f, w.toFloat(), 16f, ink)
        val display = ResourcesCompat.getFont(ctx, R.font.big_shoulders_display_var) ?: Typeface.DEFAULT_BOLD
        val archivo = ResourcesCompat.getFont(ctx, R.font.archivo_var) ?: Typeface.DEFAULT
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD63C20.toInt(); typeface = Typeface.create(display, Typeface.BOLD); textSize = 200f }
        c.drawText(year, 48f, 250f, red)
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.color; typeface = Typeface.create(archivo, Typeface.BOLD); textSize = 52f }
        var y = 340f
        for (line in wrap(title, head, w - 96f)) { c.drawText(line, 48f, y, head); y += 62f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4A463E.toInt(); typeface = archivo; textSize = 34f }
        c.drawText(wrap(body, small, w - 96f).first(), 48f, h - 48f, small)
        return bmp
    }

    private fun wrap(text: String, p: Paint, width: Float): List<String> {
        val out = ArrayList<String>(); var line = ""
        for (word in text.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) > width && line.isNotEmpty()) { out.add(line); line = word } else line = next
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }
}
