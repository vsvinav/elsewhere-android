package com.elsewhere.app.record

import com.elsewhere.app.data.Recording
import com.elsewhere.app.data.db.RawFixEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where recording stands, as the ticket prints it. */
enum class RecordState { RECORDING, PAUSED, OFF, NEEDS_PERMISSION }

enum class TicketTone { RED, INK, GHOST }

/** The status ticket's copy: "RECORDING · 1,204 fixes · last 3 min ago". */
data class Ticket(val headline: String, val fixes: String, val last: String, val tone: TicketTone)

/** The live stamp on the map: the last fix, printed as a time, a city and an age. */
data class NowStamp(val lat: Double, val lng: Double, val time: String, val city: String, val age: String)

/** A fix older than this is no longer "now". */
const val NOW_WINDOW_S = 30 * 60L

fun recordState(recording: Recording, fine: Boolean, now: Long): RecordState = when {
    !fine -> RecordState.NEEDS_PERMISSION
    !recording.enabled -> RecordState.OFF
    recording.pausedUntil > now -> RecordState.PAUSED
    else -> RecordState.RECORDING
}

/** "just now" under a minute, then minutes, hours, days. */
fun age(ts: Long, now: Long): String {
    val s = (now - ts).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

fun ticket(state: RecordState, stats: RecordStats?, now: Long): Ticket {
    val n = stats?.fixes ?: 0
    val fixes = String.format(Locale.US, "%,d", n) + if (n == 1) " fix" else " fixes"
    val last = stats?.lastFixTs?.let { "last ${age(it, now)}" } ?: "no fixes yet"
    return when (state) {
        RecordState.RECORDING -> Ticket("RECORDING", fixes, last, TicketTone.RED)
        RecordState.PAUSED -> Ticket("PAUSED", fixes, last, TicketTone.INK)
        RecordState.OFF -> Ticket("OFF", fixes, last, TicketTone.GHOST)
        RecordState.NEEDS_PERMISSION -> Ticket("NEEDS PERMISSION", fixes, last, TicketTone.INK)
    }
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** Null when there is nothing to stamp: no fix, one older than half an hour, or the recorder is off. */
fun nowStamp(fix: RawFixEntity?, now: Long, cityOf: (Double, Double) -> String?, zone: ZoneId = ZoneId.systemDefault(), recording: Boolean = true): NowStamp? {
    if (!recording || fix == null || now - fix.ts > NOW_WINDOW_S) return null
    return NowStamp(
        lat = fix.lat, lng = fix.lng,
        time = CLOCK.format(Instant.ofEpochSecond(fix.ts).atZone(zone)),
        city = cityOf(fix.lat, fix.lng) ?: "ELSEWHERE",
        age = age(fix.ts, now),
    )
}
