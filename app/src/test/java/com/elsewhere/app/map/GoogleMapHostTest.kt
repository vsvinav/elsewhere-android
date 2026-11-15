package com.elsewhere.app.map

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.MapType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.File

class GoogleMapHostTest {
    @Test fun `a camera goes to Google's position and back without a zoom shift`() {
        val cam = Cam(48.8566, 2.3522, 12.5f, bearing = 30f)
        val p = cam.toGoogle()
        assertEquals(12.5f, p.zoom)
        assertEquals(30f, p.bearing)
        assertEquals(0f, p.tilt)
        assertEquals(cam, p.toCam())
    }

    @Test fun `a position the SDK reports keeps its bearing and drops its tilt`() {
        val p = CameraPosition(LatLng(1.0, 2.0), 4f, 45f, 90f)
        assertEquals(Cam(1.0, 2.0, 4f, bearing = 90f), p.toCam())
    }

    @Test fun `without a Map ID the poster style is applied and the clutter is off`() {
        val style = MapStyleOptions("[]")
        val p = mapProperties("", style)
        assertSame(style, p.mapStyleOptions)
        assertEquals(MapType.NORMAL, p.mapType)
        assertFalse(p.isBuildingEnabled); assertFalse(p.isIndoorEnabled); assertFalse(p.isTrafficEnabled)
    }

    @Test fun `a Map ID means Cloud styling, so no JSON style is applied`() {
        val p = mapProperties("abc123", MapStyleOptions("[]"))
        assertNull(p.mapStyleOptions)
        assertFalse(p.isBuildingEnabled)
    }

    /** The raw style must stay the web's MAP_STYLES (web/src/map/style.ts): stock land, darker cream water, no points of interest. */
    @Test fun `map_style json matches the web's poster basemap`() {
        val rules = Json.parseToJsonElement(File("src/main/res/raw/map_style.json").readText()).jsonArray
        fun rule(feature: String?, element: String?) = rules.single { it.jsonObject["featureType"]?.jsonPrimitive?.content == feature && it.jsonObject["elementType"]?.jsonPrimitive?.content == element }
        fun styler(feature: String?, element: String?, k: String) = rule(feature, element).jsonObject["stylers"]!!.jsonArray.firstNotNullOf { it.jsonObject[k] }.jsonPrimitive.content
        assertEquals(21, rules.size)
        assertEquals("#efe6d2", styler(null, "geometry", "color"))
        assertEquals("#dccfb1", styler("water", "geometry", "color"))
        assertEquals("#d6c9ab", styler("road", "geometry", "color"))
        assertEquals("off", styler("poi", null, "visibility"))
        assertEquals("off", styler("transit", null, "visibility"))
        assertEquals("off", styler(null, "labels.icon", "visibility"))
    }
}
