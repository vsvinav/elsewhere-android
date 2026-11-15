package com.elsewhere.app.analytics

import com.elsewhere.app.ingest.jsRound
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// Port of web/src/analytics/format.ts; strings pinned to the en-US output of Intl.

/** JS `toFixed`: rounds the exact binary value half-up, so (0.15).toFixed(1) is "0.1" and 1.25 → "1.3". */
fun toFixed(x: Double, digits: Int): String = BigDecimal(x).setScale(digits, RoundingMode.HALF_UP).toPlainString()

fun fmtKm(km: Double): String =
    if (km >= 1000) "${toFixed(km / 1000, 1)}k km" else "${jsRound(km).toLong()} km"

private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun fmtDate(ts: Long, tzMin: Int = 0): String = DATE.format(Instant.ofEpochSecond(ts + tzMin * 60L).atOffset(ZoneOffset.UTC))
fun fmtTime(ts: Long, tzMin: Int = 0): String = TIME.format(Instant.ofEpochSecond(ts + tzMin * 60L).atOffset(ZoneOffset.UTC))

val MODE_LABEL: Map<String, String> = mapOf(
    "WALKING" to "Walking", "RUNNING" to "Running", "CYCLING" to "Cycling", "IN_PASSENGER_VEHICLE" to "Driving",
    "MOTORCYCLING" to "Motorcycle", "IN_BUS" to "Bus", "IN_SUBWAY" to "Subway", "IN_TRAIN" to "Train",
    "IN_TRAM" to "Tram", "IN_FERRY" to "Ferry", "FLYING" to "Flying",
)
