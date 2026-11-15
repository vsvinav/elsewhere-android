package com.elsewhere.app.highlights

import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.HighlightState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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

/** Sundays open with "This week" so the card reads as the week's; the rest of the week names the day. */
fun notificationTitle(h: Highlight, date: String): String {
    val sunday = LocalDate.parse(date).dayOfWeek == DayOfWeek.SUNDAY
    val lead = if (sunday) "This week" else "On this day"
    return "$lead, ${agoText(h.yearsAgo)} — you ${h.verb.text} ${h.city}."
}

private fun agoText(n: Int) = when { n <= 0 -> "earlier this year"; n == 1 -> "last year"; else -> "$n years ago" }

/** The date a notification tap asked for, until the Facts tab has shown it. Process-wide, like the intent that carried it. */
object PendingHighlight { val date = kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
