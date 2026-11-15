package com.elsewhere.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Country outlines: each ring is a flat [lng, lat, lng, lat, …] array. */
class FeatureCollection(val rings: List<DoubleArray>) {
    companion object {
        /** Reads a GeoJSON FeatureCollection of Polygon / MultiPolygon features, outer and inner rings alike. */
        fun fromJson(text: String): FeatureCollection {
            val root = Json.parseToJsonElement(text).jsonObject
            val rings = ArrayList<DoubleArray>()
            fun ring(a: JsonArray) {
                val out = DoubleArray(a.size * 2)
                a.forEachIndexed { i, c -> val p = c.jsonArray; out[i * 2] = p[0].jsonPrimitive.double; out[i * 2 + 1] = p[1].jsonPrimitive.double }
                rings.add(out)
            }
            for (f in root.getValue("features").jsonArray) {
                val g = (f.jsonObject["geometry"] as? JsonObject) ?: continue
                val coords = g["coordinates"]?.jsonArray ?: continue
                when (g["type"]?.jsonPrimitive?.content) {
                    "Polygon" -> coords.forEach { ring(it.jsonArray) }
                    "MultiPolygon" -> coords.forEach { poly -> poly.jsonArray.forEach { ring(it.jsonArray) } }
                }
            }
            return FeatureCollection(rings)
        }
    }
}

/** Where the land outlines come from; `LandLoader` is the real one, tests hand over a fixture. */
interface LandSource {
    suspend fun get(): FeatureCollection
}

/** Reads `assets/land.json` (world-atlas 1:110m countries, Antarctica dropped) once. */
@Singleton
class LandLoader @Inject constructor(@ApplicationContext private val context: Context) : LandSource {
    private val lock = Mutex()
    @Volatile private var cached: FeatureCollection? = null

    override suspend fun get(): FeatureCollection = cached ?: lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            FeatureCollection.fromJson(context.assets.open("land.json").bufferedReader().readText())
        }.also { cached = it }
    }
}
