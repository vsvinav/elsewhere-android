package com.elsewhere.app.analytics

import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Spanned
import com.elsewhere.app.data.Visit

/** Rows that overlap `range` at all; both ends inclusive. */
fun <T : Spanned> inRange(rows: List<T>, range: LongRange): List<T> =
    rows.filter { r -> r.end >= range.first && r.start <= range.last }

fun kmByMode(activities: List<Activity>): Map<String, Double> {
    val out = LinkedHashMap<String, Double>()
    for (a in activities) out[a.mode] = (out[a.mode] ?: 0.0) + a.m / 1000
    return out
}

fun distinctPlaces(visits: List<Visit>): Int = visits.mapTo(HashSet()) { it.placeId }.size
