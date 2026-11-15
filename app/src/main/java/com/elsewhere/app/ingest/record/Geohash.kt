package com.elsewhere.app.ingest.record

private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

/** Standard geohash; seven characters is a ~150 m cell, so repeated stays at one spot share an id. */
fun geohash(lat: Double, lng: Double, precision: Int = 7): String {
    var latLo = -90.0; var latHi = 90.0; var lngLo = -180.0; var lngHi = 180.0
    val out = StringBuilder(precision)
    var bit = 0; var ch = 0; var even = true
    while (out.length < precision) {
        if (even) {
            val mid = (lngLo + lngHi) / 2
            if (lng >= mid) { ch = ch shl 1 or 1; lngLo = mid } else { ch = ch shl 1; lngHi = mid }
        } else {
            val mid = (latLo + latHi) / 2
            if (lat >= mid) { ch = ch shl 1 or 1; latLo = mid } else { ch = ch shl 1; latHi = mid }
        }
        even = !even
        if (++bit == 5) { out.append(BASE32[ch]); bit = 0; ch = 0 }
    }
    return out.toString()
}

/** Place ids for stays the phone recorded itself; the export's ids are Google place ids. */
fun devicePlaceId(lat: Double, lng: Double, precision: Int = 7): String = "dev:" + geohash(lat, lng, precision)
