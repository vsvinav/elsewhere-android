package com.elsewhere.app.sonar

import com.elsewhere.app.data.db.RawFixEntity
import com.elsewhere.app.data.db.VisitEntity
import java.time.LocalDate

/** One [DayAt] per visit per local calendar day it spans; `start`/`end` in seconds, `tz` in minutes. */
fun dayRows(visits: List<VisitEntity>): List<DayAt> {
    val out = ArrayList<DayAt>(visits.size)
    for (v in visits) {
        val first = Math.floorDiv(v.start + v.tz * 60L, 86_400L)
        val last = Math.floorDiv(maxOf(v.end - 1, v.start) + v.tz * 60L, 86_400L)
        for (d in first..minOf(last, first + 60)) out += DayAt(LocalDate.ofEpochDay(d).toString(), v.lat, v.lng)
    }
    return out
}

fun RawFixEntity.toSonarFix() = Fix(ts * 1000, lat, lng)

/**
 * My side of the test, judged around [at] — the friend's position — since that is where the meeting would be.
 * Within a mile the two circles overlap almost entirely, so this is the same question the friend answered
 * about their own spot.
 */
fun mySide(myFix: Fix, history: List<DayAt>, myFixes: List<Fix>, at: Fix, now: Long): Side =
    Side(myFix, isHabitat(history, at, now), absenceDays(history, at, stintStart(myFixes, at)))
