package com.elsewhere.app.ui.tabs.dates

import com.elsewhere.app.data.Record
import com.elsewhere.app.ui.nav.doorId
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

// Port of web/src/tabs/Trips/deck.ts.

/** One trip as the deck deals it: the route, when, and the four figures the cards are compared on. */
data class Card(
    val tripId: String, val title: String, val departed: Long, val returned: Long,
    val nights: Int,
    /** Every mode added up, in the record's kilometres. */
    val km: Double, val flights: Int, val farthestKm: Double,
)

/** Which card wins each stat: the sign of a − b, as -1, 0 or 1. */
data class Compared(val nights: Int, val km: Int, val flights: Int, val farthest: Int)

/** First index in `sorted` whose value is >= `t`. */
private fun lowerBound(sorted: LongArray, t: Long): Int {
    var lo = 0; var hi = sorted.size
    while (lo < hi) { val mid = (lo + hi) ushr 1; if (sorted[mid] < t) lo = mid + 1 else hi = mid }
    return lo
}

/**
 * The deck, newest trip first. Flights are counted off one sorted list of departure times with two binary
 * searches per trip, so a record of thousands of trips over a long flight log is O(A log A + T log A).
 */
fun deckCards(data: Record): List<Card> {
    val starts = LongArray(data.activities.count { it.mode == "FLYING" })
    var i = 0
    for (a in data.activities) if (a.mode == "FLYING") starts[i++] = a.start
    starts.sort()
    return data.trips.sortedByDescending { it.start }.map { t ->
        Card(
            tripId = t.doorId, title = t.anchorCities.joinToString(" → ").ifEmpty { "TRIP" },
            departed = t.start, returned = t.end, nights = t.nights, km = t.totalKmByMode.values.sum(),
            flights = lowerBound(starts, t.end + 1) - lowerBound(starts, t.start),
            farthestKm = t.maxDistFromHomeKm,
        )
    }
}

private fun sign(x: Double): Int = if (x > 0) 1 else if (x < 0) -1 else 0

fun compare(a: Card, b: Card) = Compared(
    nights = sign((a.nights - b.nights).toDouble()), km = sign(a.km - b.km),
    flights = sign((a.flights - b.flights).toDouble()), farthest = sign(a.farthestKm - b.farthestKm),
)

/** What a card answers to: its route plus the month and year it departed, lower-cased. */
private fun haystack(c: Card): String {
    val d = Instant.ofEpochSecond(c.departed).atOffset(ZoneOffset.UTC)
    return "${c.title.lowercase(Locale.US)} ${d.month.name.lowercase(Locale.US)} ${d.year}"
}

/** Cards whose route or departure month/year contains every token of `q` ("lisbon 2024"). Case-insensitive. */
fun searchCards(cards: List<Card>, q: String): List<Card> {
    val tokens = q.lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return cards
    return cards.filter { c -> val h = haystack(c); tokens.all { it in h } }
}
