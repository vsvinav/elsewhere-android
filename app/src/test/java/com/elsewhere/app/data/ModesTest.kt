package com.elsewhere.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModesTest {
    @Test fun `every mode the record knows has a short plain name`() {
        assertEquals("Walk", modeLabel("WALKING"))
        assertEquals("Run", modeLabel("RUNNING"))
        assertEquals("Cycle", modeLabel("CYCLING"))
        assertEquals("Car", modeLabel("IN_PASSENGER_VEHICLE"))
        assertEquals("Bus", modeLabel("IN_BUS"))
        assertEquals("Train", modeLabel("IN_TRAIN"))
        assertEquals("Metro", modeLabel("IN_SUBWAY"))
        assertEquals("Tram", modeLabel("IN_TRAM"))
        assertEquals("Ferry", modeLabel("IN_FERRY"))
        assertEquals("Flight", modeLabel("FLYING"))
        assertEquals("Motorbike", modeLabel("MOTORCYCLING"))
    }

    @Test fun `anything else is Other`() {
        assertEquals("Other", modeLabel("UNKNOWN"))
        assertEquals("Other", modeLabel("IN_GONDOLA_LIFT"))
        assertEquals("Other", modeLabel(""))
    }

    @Test fun `the confirm chips map to the modes they write`() {
        assertEquals(listOf("Car", "Bus", "Train", "Metro", "Other"), CONFIRM_MODES.map { modeLabel(it) })
        assertEquals(listOf("IN_PASSENGER_VEHICLE", "IN_BUS", "IN_TRAIN", "IN_SUBWAY", "UNKNOWN"), CONFIRM_MODES)
    }
}
