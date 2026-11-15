package com.elsewhere.app.sonar

import kotlinx.serialization.Serializable

const val RADIUS_M = 1609.0
const val HABITAT_DAYS = 30
const val HABITAT_MIN_DAYS = 4
const val ABSENCE_MIN_DAYS = 14
const val DEBOUNCE_MS = 24 * 3600 * 1000L
const val FRESH_MS = 10 * 60 * 1000L
private const val DAY_MS = 86_400_000L

data class Fix(val ts: Long, val lat: Double, val lng: Double)

/** One row per visit per local day it spans; `localDay` is `yyyy-MM-dd` in the visit's zone. */
data class DayAt(val localDay: String, val lat: Double, val lng: Double)

data class Side(val fix: Fix, val habitat: Boolean, val absenceDays: Int?)

@Serializable
data class Debounce(val lastHitByPair: Map<String, Long> = emptyMap())

sealed interface Decision {
    data object Quiet : Decision
    data class Fire(val hitId: String) : Decision
    data class Echo(val hitId: String) : Decision
}

fun metres(a: Fix, b: Fix): Double = metres(a.lat, a.lng, b.lat, b.lng)

fun metres(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371e3
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
    return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

fun within(a: Fix, b: Fix, r: Double = RADIUS_M): Boolean = metres(a, b) <= r

/** Quick reject before the trig: a degree of latitude is ~111 km, so anything past the box can't be within the radius. */
private fun near(lat: Double, lng: Double, at: Fix, r: Double = RADIUS_M): Boolean {
    val dLat = Math.abs(lat - at.lat) * 111_320.0
    if (dLat > r) return false
    val dLng = Math.abs(lng - at.lng) * 111_320.0 * Math.cos(Math.toRadians(at.lat))
    if (dLng > r * 1.01) return false
    return metres(lat, lng, at.lat, at.lng) <= r
}

/** Habitat: at least [min] distinct local days in the last [days] had a visit within the radius of [at]. */
fun isHabitat(history: List<DayAt>, at: Fix, now: Long, days: Int = HABITAT_DAYS, min: Int = HABITAT_MIN_DAYS): Boolean {
    val floor = localDay(now - days * DAY_MS)
    val seen = HashSet<String>()
    for (d in history) {
        if (d.localDay < floor) continue
        if (near(d.lat, d.lng, at) && seen.add(d.localDay) && seen.size >= min) return true
    }
    return false
}

/** Days between the start of the current stint near [at] and the last visit near it before that stint; null when never. */
fun absenceDays(history: List<DayAt>, at: Fix, stintStart: Long): Int? {
    val stintDay = localDay(stintStart)
    val last = history.asSequence().filter { it.localDay < stintDay && near(it.lat, it.lng, at) }.maxOfOrNull { it.localDay } ?: return null
    return ((epochDay(stintDay) - epochDay(last))).toInt()
}

/** Walk back through my recent fixes while each stays within the radius of [at]. */
fun stintStart(myFixes: List<Fix>, at: Fix): Long {
    var start = at.ts
    for (f in myFixes.sortedByDescending { it.ts }) {
        if (f.ts > at.ts) continue
        if (!within(f, at)) break
        start = f.ts
    }
    return start
}

/** The newcomer is whoever arrived later; on a tie the shorter absence counts, which can only hold fire. */
fun newcomerAbsence(me: Side, friend: Side): Int {
    val mine = me.absenceDays ?: Int.MAX_VALUE
    val theirs = friend.absenceDays ?: Int.MAX_VALUE
    return when {
        friend.fix.ts > me.fix.ts -> theirs
        me.fix.ts > friend.fix.ts -> mine
        else -> minOf(mine, theirs)
    }
}

fun eligible(me: Side, friend: Side, now: Long): Boolean =
    now - me.fix.ts <= FRESH_MS && now - friend.fix.ts <= FRESH_MS &&
        within(me.fix, friend.fix) &&
        !me.habitat && !friend.habitat &&
        newcomerAbsence(me, friend) >= ABSENCE_MIN_DAYS

fun debounced(d: Debounce, pairId: String, now: Long): Boolean = (d.lastHitByPair[pairId] ?: Long.MIN_VALUE) > now - DEBOUNCE_MS

fun recordHit(d: Debounce, pairId: String, now: Long): Debounce = Debounce(d.lastHitByPair + (pairId to now))

/** The one entry point; the friend's `hit` field, if any, wins over my own evaluation so both sides agree. */
fun decide(me: Side, friend: Side, pairId: String, now: Long, d: Debounce, incomingHit: String?): Decision =
    when {
        debounced(d, pairId, now) -> Decision.Quiet
        incomingHit != null -> Decision.Echo(incomingHit)
        eligible(me, friend, now) -> Decision.Fire("$pairId:$now")
        else -> Decision.Quiet
    }

/** UTC day string; callers that know the zone can pass their own `localDay` rows, this is for thresholds only. */
fun localDay(ts: Long): String {
    val d = Math.floorDiv(ts, DAY_MS)
    return java.time.LocalDate.ofEpochDay(d).toString()
}

private fun epochDay(day: String): Long = java.time.LocalDate.parse(day).toEpochDay()
