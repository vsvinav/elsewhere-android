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

    @Test fun `kilometres by mode fold into one row per name, Other included, biggest first`() {
        val km = kmByLabel(mapOf("IN_VEHICLE" to 30.0, "UNKNOWN" to 20.0, "FLYING" to 500.0, "IN_TAXI" to 5.0, "WALKING" to 12.0))
        assertEquals(listOf("Flight" to 500.0, "Other" to 55.0, "Walk" to 12.0), km)
        assertEquals(1, km.count { it.first == "Other" })
    }

    @Test fun `in a sentence a mode reads lowercase, and an unnamed one reads by road`() {
        assertEquals("train", modeInProse("IN_TRAIN"))
        assertEquals("car", modeInProse("IN_PASSENGER_VEHICLE"))
        assertEquals("road", modeInProse("IN_VEHICLE"))
        assertEquals("road", modeInProse("UNKNOWN"))
    }

    @Test fun `the confirm chips map to the modes they write`() {
        assertEquals(listOf("Car", "Bus", "Train", "Metro", "Other"), CONFIRM_MODES.map { modeLabel(it) })
        assertEquals(listOf("IN_PASSENGER_VEHICLE", "IN_BUS", "IN_TRAIN", "IN_SUBWAY", "UNKNOWN"), CONFIRM_MODES)
    }
}
