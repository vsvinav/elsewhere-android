package com.elsewhere.app.ingest

import com.elsewhere.app.data.FrequentPlace
import com.elsewhere.app.data.ImportReport
import com.elsewhere.app.data.Pt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.ZoneOffset

// Port of pipeline/parse.py: a Google Timeline export → flat records, with the same skip reasons.
data class PVisit(
    val id: Int, val start: Long, val end: Long, val tz: Int, val placeId: String, val lat: Double, val lng: Double,
    val type: String, val p: Double, val lvl: Int, val city: String? = null, val cc: String? = null, val dist: Double? = null,
)
data class PActivity(
    val id: Int, val start: Long, val end: Long, val tz: Int, val sLat: Double, val sLng: Double, val eLat: Double, val eLng: Double,
    val m: Double, val mode: String, val p: Double, val eCity: String? = null,
)
data class PPath(val start: Long, val end: Long, val pts: List<Pt>)
data class PMemory(val start: Long, val end: Long, val km: Double, val dests: List<String>)
data class Parsed(
    val visits: List<PVisit>, val activities: List<PActivity>, val paths: List<PPath>, val memories: List<PMemory>,
    val frequentPlaces: List<FrequentPlace>, val report: ImportReport,
)

private val LATLNG = Regex("""^\s*(-?\d+(?:\.\d+)?)°?\s*,\s*(-?\d+(?:\.\d+)?)°?\s*\z""")
private val TS = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})\z""")
// Some exports carry terminal escape sequences from how they were copied.
private val ESCAPES = Regex("""\u001b\[[0-9;?]*[A-Za-z]""")

internal class Bad(val reason: String) : Exception(reason)

private fun str(v: JsonElement?): String? = (v as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun latlng(s: JsonElement?): Pair<Double, Double> {
    val m = str(s)?.let { LATLNG.find(it) } ?: throw Bad("bad_latlng")
    val lat = m.groupValues[1].toDouble()
    val lng = m.groupValues[2].toDouble()
    if (!(lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180)) throw Bad("bad_latlng")
    return lat to lng
}

/** Epoch seconds and the string's own offset in minutes. */
internal fun ts(s: JsonElement?): Pair<Long, Int> {
    val m = str(s)?.let { TS.find(it) } ?: throw Bad("bad_timestamp")
    val g = m.groupValues
    val z = g[7]
    val off = if (z == "Z") 0 else (if (z[0] == '-') -1 else 1) * (z.substring(1, 3).toInt() * 60 + z.substring(4, 6).toInt())
    val utc = LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        .toEpochSecond(ZoneOffset.UTC) - off * 60
    return utc to off
}

/** A finite number, or a numeric string, else `d`. */
internal fun num(v: JsonElement?, d: Double = 0.0): Double {
    val p = v as? JsonPrimitive ?: return d
    if (!p.isString) return p.doubleOrNull?.takeIf { it.isFinite() } ?: d
    if (p.content.isEmpty()) return d
    return p.content.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: d
}

private fun field(o: JsonElement?, k: String): JsonElement {
    if (o is JsonObject && o.containsKey(k)) return o.getValue(k)
    throw Bad("missing_field")
}
/** Absent keys and JSON nulls both read as "not there", like `?? default` in TS. */
private fun opt(o: JsonElement?, k: String): JsonElement? = (o as? JsonObject)?.get(k)?.takeUnless { it is JsonNull }

private fun isInteger(v: JsonElement?): Boolean {
    val p = v as? JsonPrimitive ?: return false
    if (p.isString) return false
    val d = p.doubleOrNull ?: return false
    return d.isFinite() && d == Math.floor(d)
}

/** The export's string offset is the exporting phone's zone; the segment field carries the local one. */
private fun segTz(seg: JsonElement, stringTz: Int): Int {
    val off = opt(seg, "startTimeTimezoneUtcOffsetMinutes")
    return if (isInteger(off)) off!!.jsonPrimitive.content.toDouble().toInt() else stringTz
}

/** JS `Math.round`: half rounds toward +∞, unlike Kotlin's half-away-from-zero. */
private fun jsRound(x: Double): Double = Math.floor(x + 0.5)
private fun trunc(x: Double): Double = if (x < 0) Math.ceil(x) else Math.floor(x)

fun parseTimeline(text: String): Parsed {
    val doc = Json.parseToJsonElement(ESCAPES.replace(text, "")) as? JsonObject ?: JsonObject(emptyMap())
    val visits = ArrayList<PVisit>()
    val activities = ArrayList<PActivity>()
    val paths = ArrayList<PPath>()
    val memories = ArrayList<PMemory>()
    val frequentPlaces = ArrayList<FrequentPlace>()
    val skipped = LinkedHashMap<String, Int>()
    var segmentsTotal = 0
    var parsedCount = 0
    val skip = { r: String -> skipped[r] = (skipped[r] ?: 0) + 1 }

    val segs = (doc["semanticSegments"] as? JsonArray) ?: JsonArray(emptyList())
    segs.forEachIndexed { idx, seg ->
        segmentsTotal++
        try {
            when {
                opt(seg, "visit") != null -> {
                    val (start, stz) = ts(field(seg, "startTime")); val (end, _) = ts(field(seg, "endTime"))
                    val v = field(seg, "visit"); val tc = field(v, "topCandidate")
                    val (lat, lng) = latlng(field(field(tc, "placeLocation"), "latLng"))
                    visits += PVisit(
                        id = idx, start = start, end = end, tz = segTz(seg, stz), placeId = field(tc, "placeId").asString(), lat = lat, lng = lng,
                        type = opt(tc, "semanticType")?.asString() ?: "UNKNOWN",
                        p = num(opt(v, "probability") ?: opt(tc, "probability")),
                        lvl = trunc(num(opt(v, "hierarchyLevel"))).toInt(),
                    )
                }
                opt(seg, "activity") != null -> {
                    val (start, stz) = ts(field(seg, "startTime")); val (end, _) = ts(field(seg, "endTime"))
                    val a = field(seg, "activity"); val tc = field(a, "topCandidate")
                    val (sLat, sLng) = latlng(field(field(a, "start"), "latLng")); val (eLat, eLng) = latlng(field(field(a, "end"), "latLng"))
                    activities += PActivity(
                        id = idx, start = start, end = end, tz = segTz(seg, stz), sLat = sLat, sLng = sLng, eLat = eLat, eLng = eLng,
                        m = jsRound(num(opt(a, "distanceMeters"))), mode = opt(tc, "type")?.asString() ?: "UNKNOWN",
                        p = num(opt(a, "probability") ?: opt(tc, "probability")),
                    )
                }
                opt(seg, "timelinePath") != null -> {
                    val (start, _) = ts(field(seg, "startTime")); val (end, _) = ts(field(seg, "endTime"))
                    val raw = field(seg, "timelinePath") as? JsonArray ?: throw Bad("missing_field")
                    val pts = raw.map { p -> val (lat, lng) = latlng(field(p, "point")); Pt(lat, lng, ts(field(p, "time")).first) }
                    paths += PPath(start, end, pts)
                }
                opt(seg, "timelineMemory") != null -> {
                    val (start, _) = ts(field(seg, "startTime")); val (end, _) = ts(field(seg, "endTime"))
                    val trip = opt(field(seg, "timelineMemory"), "trip") ?: JsonObject(emptyMap())
                    val dests = (opt(trip, "destinations") as? JsonArray ?: JsonArray(emptyList()))
                        .mapNotNull { d -> str(opt(opt(d, "identifier"), "placeId")) }
                    memories += PMemory(start, end, trunc(num(opt(trip, "distanceFromOriginKms"))), dests)
                }
                else -> { skip("unknown_segment"); return@forEachIndexed }
            }
            parsedCount++
        } catch (e: Bad) { skip(e.reason) } catch (e: RuntimeException) { skip("missing_field") }
    }

    val up = doc["userLocationProfile"]
    for (p in (opt(up, "frequentPlaces") as? JsonArray) ?: JsonArray(emptyList())) {
        try {
            val (lat, lng) = latlng(field(p, "placeLocation"))
            frequentPlaces += FrequentPlace(placeId = field(p, "placeId").asString(), lat = lat, lng = lng, label = str(opt(p, "label")))
        } catch (_: RuntimeException) { /* skip */ }
    }

    visits.sortBy { it.start }; activities.sortBy { it.start }; paths.sortBy { it.start }
    return Parsed(visits, activities, paths, memories, frequentPlaces, ImportReport(segmentsTotal, parsedCount, skipped))
}

/** JS `String(x)`: strings as-is, other primitives by their JSON text. */
private fun JsonElement.asString(): String = (this as? JsonPrimitive)?.let { it.contentOrNull ?: it.toString() } ?: toString()
