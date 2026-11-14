package com.elsewhere.app.analytics

// Port of web/src/analytics/lens.ts

/** A lens plays one kind of movement only: Play cuts from leg to leg, the map draws only that ink. */
enum class Lens(val key: String, val label: String, val color: String) {
    ALL("all", "Everything", "#141414"),
    FLY("fly", "Flights", "#d63c20"),
    DRIVE("drive", "Drives", "#141414"),
    /** Transit takes a deep blue, the one colour outside the poster's four. */
    TRANSIT("transit", "Transit", "#1e4f8f"),
    FOOT("foot", "On foot", "#e9b531"),
}

val LENSES: List<Lens> = Lens.entries

private val GROUP: Map<String, Lens> = mapOf(
    "FLYING" to Lens.FLY,
    "IN_PASSENGER_VEHICLE" to Lens.DRIVE, "MOTORCYCLING" to Lens.DRIVE,
    "IN_SUBWAY" to Lens.TRANSIT, "IN_BUS" to Lens.TRANSIT, "IN_TRAIN" to Lens.TRANSIT, "IN_TRAM" to Lens.TRANSIT, "IN_FERRY" to Lens.TRANSIT,
    "WALKING" to Lens.FOOT, "CYCLING" to Lens.FOOT, "RUNNING" to Lens.FOOT,
)

fun lensOf(mode: String): Lens = GROUP[mode] ?: Lens.DRIVE
fun inLens(mode: String, lens: Lens): Boolean = lens == Lens.ALL || lensOf(mode) == lens
