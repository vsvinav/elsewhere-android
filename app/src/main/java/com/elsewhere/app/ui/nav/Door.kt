package com.elsewhere.app.ui.nav

import com.elsewhere.app.data.Trip
import com.elsewhere.app.ui.Tab

/**
 * Where a printed figure leads when tapped: every number on the posters opens the map on the window, the
 * stop or the stops that made it. `label` is the figure as printed, for the tag the map wears on the way back.
 */
sealed interface Door {
    val label: String

    /** A date window, epoch seconds inclusive; the map shows every stop in it. */
    data class Range(val from: Long, val to: Long, override val label: String) : Door

    /** One stop, opened and framed. `at` is a moment the figure refers to; the window widens to hold it if it must. */
    data class Place(val placeId: String, val at: Long? = null, override val label: String) : Door

    /** One of the record's trips, by [Trip.doorId]; the window becomes the trip's span. */
    data class Trip(val tripId: String, val from: Long, val to: Long, override val label: String) : Door

    /** A set of stops framed together, the window left alone. */
    data class Places(val placeIds: List<String>, override val label: String) : Door
}

/** Trips carry no id of their own; the span is unique within a record. */
val Trip.doorId: String get() = "$start-$end"

/** The same door, labelled as another figure prints it. */
fun Door.withLabel(label: String): Door = when (this) {
    is Door.Range -> copy(label = label)
    is Door.Place -> copy(label = label)
    is Door.Trip -> copy(label = label)
    is Door.Places -> copy(label = label)
}

/** The tab a door was opened from, so the map can send the reader back to the same bill. */
data class Origin(val label: String, val backTab: Tab)
