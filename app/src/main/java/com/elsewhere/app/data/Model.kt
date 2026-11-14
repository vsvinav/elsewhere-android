package com.elsewhere.app.data

import kotlinx.serialization.Serializable

enum class Units { KM, MI }
enum class MapProvider { AUTO, GOOGLE, OSM }
enum class Role { HOME, WORK }

/** Anything with a position, so distance helpers work across visits, places and homes. */
interface Located { val lat: Double; val lng: Double }

@Serializable data class LatLng(override val lat: Double, override val lng: Double) : Located

/** Anything with a time span, so range filters work across visits, activities and paths. */
interface Spanned { val start: Long; val end: Long }

/** One GPS sample: `ts` is epoch seconds. */
@Serializable data class Pt(override val lat: Double, override val lng: Double, val ts: Long) : Located

@Serializable data class Visit(
    val id: Int, override val start: Long, override val end: Long, val tz: Int, val placeId: String,
    override val lat: Double, override val lng: Double, val type: String, val p: Double, val lvl: Int,
) : Spanned, Located

@Serializable data class Activity(
    val id: Int, override val start: Long, override val end: Long, val tz: Int,
    val sLat: Double, val sLng: Double, val eLat: Double, val eLng: Double,
    val m: Double, val mode: String, val p: Double,
) : Spanned

@Serializable data class Path(override val start: Long, override val end: Long, val pts: List<Pt>) : Spanned

@Serializable data class Place(
    override val lat: Double, override val lng: Double, val label: String?, val role: Role?,
    val city: String?, val countryCode: String?, val nVisits: Int,
    val totalHours: Double, val firstTs: Long, val lastTs: Long,
    val name: String?, val address: String? = null,
    val types: List<String> = emptyList(), val primaryType: String? = null, val photo: String? = null,
) : Located

@Serializable data class Daily(
    val date: String, val kmByMode: Map<String, Double>, val nVisits: Int, val nPlaces: Int,
    val nNewPlaces: Int, val maxDistFromHomeKm: Double, val hoursHome: Double,
    val hoursWork: Double, val hoursOther: Double, val hoursTransit: Double, val cities: List<String>,
)

@Serializable data class Memory(val distanceFromOriginKm: Double, val destinationPlaceIds: List<String>)

@Serializable data class Trip(
    override val start: Long, override val end: Long, val nights: Int, val maxDistFromHomeKm: Double,
    val totalKmByMode: Map<String, Double>, val anchorCities: List<String>, val memory: Memory?,
) : Spanned

@Serializable data class FrequentPlace(val placeId: String, val lat: Double, val lng: Double, val label: String?)

/** The visitor's declared homes and work, as derivation used them. */
data class RecordProfile(val home: LatLng, val homes: List<Home>, val works: List<Work>, val frequentPlaces: List<FrequentPlace>)

data class ImportReport(val segmentsTotal: Int, val parsed: Int, val skippedByReason: Map<String, Int>)

data class Meta(val sourceFile: String, val sourceSha256: String, val builtAt: Long, val range: LongRange, val importReport: ImportReport)

data class Record(
    val visits: List<Visit>, val activities: List<Activity>,
    /** Decimated GPS record for the printed strips. `paths` is the full record, loaded only for the map. */
    val strokes: List<Path>, val paths: List<Path>,
    val places: Map<String, Place>, val daily: List<Daily>, val trips: List<Trip>,
    val profile: RecordProfile, val meta: Meta,
)
