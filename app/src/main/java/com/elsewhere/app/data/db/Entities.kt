package com.elsewhere.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.elsewhere.app.data.Pt
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SOURCE_IMPORT = "import"
const val SOURCE_DEVICE = "device"

// Rows mirror the parser output (ingest.Parsed), not the derived Record: derivation is cheap and
// depends on the profile, parsing is not and depends only on the file.

@Entity(tableName = "visits")
data class VisitEntity(
    @PrimaryKey val id: Int, val start: Long, val end: Long, val tz: Int, val placeId: String,
    val lat: Double, val lng: Double, val type: String, val p: Double, val lvl: Int,
    val city: String?, val cc: String?, val dist: Double?,
    @ColumnInfo(defaultValue = "import") val source: String = SOURCE_IMPORT,
)

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: Int, val start: Long, val end: Long, val tz: Int,
    val sLat: Double, val sLng: Double, val eLat: Double, val eLng: Double,
    val m: Double, val mode: String, val p: Double, val eCity: String?,
    @ColumnInfo(defaultValue = "import") val source: String = SOURCE_IMPORT,
)

@Entity(tableName = "paths")
data class PathEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val start: Long, val end: Long, val pts: ByteArray,
    @ColumnInfo(defaultValue = "import") val source: String = SOURCE_IMPORT,
) {
    override fun equals(other: Any?): Boolean = other is PathEntity && other.id == id && other.start == start && other.end == end && other.pts.contentEquals(pts)
    override fun hashCode(): Int = id.hashCode()
}

/** Every location the phone saw while recording, kept ~90 days so the stays can be re-segmented. */
@Entity(tableName = "raw_fixes")
data class RawFixEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val ts: Long, val lat: Double, val lng: Double, val accuracyM: Float,
    val speedMps: Float?, val altitude: Double?, val activity: String?, val batteryPct: Int?,
)

@Entity(tableName = "memories")
data class MemoryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val start: Long, val end: Long, val km: Double, val dests: String)

@Entity(tableName = "frequent_places")
data class FrequentPlaceEntity(@PrimaryKey val placeId: String, val lat: Double, val lng: Double, val label: String?)

/** Filled by Places enrichment later; declared now so the schema does not move. */
@Entity(tableName = "place_enrichment")
data class PlaceEnrichmentEntity(
    @PrimaryKey val placeId: String, val name: String?, val address: String?,
    val types: String, val primaryType: String?, val photo: String?,
)

@Entity(tableName = "import_meta")
data class ImportMetaEntity(
    @PrimaryKey val id: Int = 1, val sourceFile: String, val sourceSha256: String, val builtAt: Long,
    val segmentsTotal: Int, val parsed: Int, val skippedJson: String,
)

private const val PT_BYTES = 24

/** `List<Pt>` as little-endian doubles lat, lng, ts per point. */
fun encodePts(pts: List<Pt>): ByteArray {
    val buf = ByteBuffer.allocate(pts.size * PT_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    for (p in pts) { buf.putDouble(p.lat); buf.putDouble(p.lng); buf.putDouble(p.ts.toDouble()) }
    return buf.array()
}

fun decodePts(bytes: ByteArray): List<Pt> {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val out = ArrayList<Pt>(bytes.size / PT_BYTES)
    while (buf.remaining() >= PT_BYTES) out.add(Pt(buf.getDouble(), buf.getDouble(), buf.getDouble().toLong()))
    return out
}
