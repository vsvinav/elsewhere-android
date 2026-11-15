package com.elsewhere.app.highlights

import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.HighlightState
import com.elsewhere.app.ui.tabs.highlights.headline
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

// The pure half of the daily "On this day" notification: when it runs and whether it says anything.

/** The default hour, 08:00, as a minute of the day. */
const val DEFAULT_MINUTE = 8 * 60

/** Milliseconds from `nowMs` until the next wall-clock `minuteOfDay` in `zone`: today if still ahead, else tomorrow. */
fun nextRunDelayMs(nowMs: Long, minuteOfDay: Int, zone: ZoneId): Long {
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val at = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    var next = now.toLocalDate().atTime(at).atZone(zone)
    if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(at).atZone(zone)
    return next.toInstant().toEpochMilli() - nowMs
}

/** Only a found highlight prints; quiet days, short records and empty records stay silent. */
fun shouldNotify(state: HighlightState): Boolean = state is HighlightState.Found

/** One line for the notification body: the facts, or the city when there are none. */
fun notificationBody(h: Highlight): String = h.facts.joinToString(" · ") { it.label }.ifEmpty { h.city }

/** The card's own headline: "This week" when the pick was widened, "On this day" otherwise. */
fun notificationTitle(h: Highlight): String = headline(h)

/** The date a notification tap asked for, until the Facts tab has shown it. Process-wide, like the intent that carried it. */
object PendingHighlight {
    val date = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** Takes the day a notification tap carries, whether it arrives in onCreate or onNewIntent. */
    fun take(intent: android.content.Intent?) { intent?.getStringExtra(HighlightWorker.EXTRA_OPEN)?.let { date.value = it } }
}
