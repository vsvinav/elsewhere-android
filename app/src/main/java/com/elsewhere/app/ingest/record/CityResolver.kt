package com.elsewhere.app.ingest.record

import com.elsewhere.app.ingest.Cities
import com.elsewhere.app.ingest.geocoder

/** The parser's city tag for stays the phone recorded: the nearest listed city, if one is within reach. */
class CityResolver(cities: Cities, private val maxKm: Double = 50.0) {
    private val geo = geocoder(cities)

    fun resolve(lat: Double, lng: Double): Pair<String?, String?> {
        val hit = geo(lat, lng)?.takeIf { it.km <= maxKm } ?: return null to null
        return hit.name.ifEmpty { null } to hit.cc.ifEmpty { null }
    }
}
