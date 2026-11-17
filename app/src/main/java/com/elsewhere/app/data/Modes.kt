package com.elsewhere.app.data

// The one place a mode gets its printed name; the web's `modes.ts` says the same words.

private val MODE_LABELS = mapOf(
    "WALKING" to "Walk", "RUNNING" to "Run", "CYCLING" to "Cycle", "IN_PASSENGER_VEHICLE" to "Car",
    "IN_BUS" to "Bus", "IN_TRAIN" to "Train", "IN_SUBWAY" to "Metro", "IN_TRAM" to "Tram", "IN_FERRY" to "Ferry",
    "FLYING" to "Flight", "MOTORCYCLING" to "Motorbike",
)

/** `IN_PASSENGER_VEHICLE` → `Car`; a mode nobody named is `Other`. */
fun modeLabel(mode: String): String = MODE_LABELS[mode] ?: "Other"

/** A leg is worth asking about below this confidence. */
const val SURE_P = 0.75

/** What "How did you get there?" offers, in order: Car · Bus · Train · Metro · Other. */
val CONFIRM_MODES = listOf("IN_PASSENGER_VEHICLE", "IN_BUS", "IN_TRAIN", "IN_SUBWAY", "UNKNOWN")
