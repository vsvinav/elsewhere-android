package com.elsewhere.app.analytics

import com.elsewhere.app.ingest.jsRound
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Port of web/src/analytics/format.ts; strings pinned to the en-US output of Intl.

fun fmtKm(km: Double): String =
    if (km >= 1000) "${String.format(Locale.US, "%.1f", km / 1000)}k km" else "${jsRound(km).toLong()} km"

private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun fmtDate(ts: Long, tzMin: Int = 0): String = DATE.format(Instant.ofEpochSecond(ts + tzMin * 60L).atOffset(ZoneOffset.UTC))
fun fmtTime(ts: Long, tzMin: Int = 0): String = TIME.format(Instant.ofEpochSecond(ts + tzMin * 60L).atOffset(ZoneOffset.UTC))

val MODE_LABEL: Map<String, String> = mapOf(
    "WALKING" to "Walking", "RUNNING" to "Running", "CYCLING" to "Cycling", "IN_PASSENGER_VEHICLE" to "Driving",
    "MOTORCYCLING" to "Motorcycle", "IN_BUS" to "Bus", "IN_SUBWAY" to "Subway", "IN_TRAIN" to "Train",
    "IN_TRAM" to "Tram", "IN_FERRY" to "Ferry", "FLYING" to "Flying",
)
