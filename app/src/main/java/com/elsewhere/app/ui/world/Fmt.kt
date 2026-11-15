package com.elsewhere.app.ui.world

import com.elsewhere.app.analytics.MODE_LABEL
import com.elsewhere.app.data.Units
import com.elsewhere.app.ingest.jsRound
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Port of web/src/world/fmt.ts; strings pinned to the en-US output of Intl.

private const val MI = 0.621371
private val DAY = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
private val MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.US)
private val MONTH_LONG = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

fun n0(x: Double): String = String.format(Locale.US, "%,d", jsRound(x).toLong())
fun n0(x: Int): String = n0(x.toDouble())

/** `MAY 12, 2024`. */
fun date(ts: Long): String = DAY.format(Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC)).uppercase(Locale.US)
fun dateISO(s: String): String = DAY.format(LocalDate.parse(s)).uppercase(Locale.US)
/** `MAY 24` for a `2024-05` key. */
fun month(key: String): String = MONTH.format(LocalDate.parse("$key-01")).uppercase(Locale.US)
/** `May 2024`, for the nights span. */
fun monthLong(ts: Long): String = MONTH_LONG.format(Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC))
fun yearOf(ts: Long): Int = Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).year

fun label(mode: String): String = MODE_LABEL[mode]
    ?: mode.removePrefix("IN_").replace('_', ' ').lowercase(Locale.US).replaceFirstChar { it.uppercaseChar() }

fun hours(h: Double): String = if (h >= 48) "${jsRound(h / 24).toLong()} d" else "${jsRound(h).toLong()} h"

val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** The web's `useUnits`: `u` is the short label, `cv` converts kilometres to the profile's unit. */
val Units.u: String get() = name.lowercase(Locale.US)
fun Units.cv(km: Double): Double = if (this == Units.MI) km * MI else km

fun plural(n: Int, one: String, many: String = one + "s"): String = if (n == 1) one else many
