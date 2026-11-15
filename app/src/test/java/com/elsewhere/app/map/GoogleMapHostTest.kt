package com.elsewhere.app.map

import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
