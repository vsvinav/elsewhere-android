package com.elsewhere.app.ui.tabs.stops

/**
 * The address a tag shows: the first line of the Google Places address with the city ("1 Devonshire Pl, Boston"),
 * else what Nominatim said ("street, area"), else nothing. A first line that is blank or just the city is no address.
 */
fun stopAddress(placesAddress: String?, city: String?, nominatim: String?): String? {
    val line = placesAddress?.substringBefore(',')?.trim().orEmpty()
    if (line.isEmpty() || line == city) return nominatim
    return listOfNotNull(line, city).joinToString(", ")
}
