package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.daily
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Twin of web/src/analytics/insights.test.ts. */
class InsightsTest {
    companion object {
        val JAN = Fx.utc(2025, 0); val FEB = Fx.utc(2025, 1); val MAR = Fx.utc(2025, 2)
        val data = Fx.record(
            visits = listOf(
                visit(JAN, JAN + 8 * H, "home", "INFERRED_HOME"),
                visit(JAN + 9 * H, JAN + 17 * H, "work", "INFERRED_WORK"),
                visit(JAN + 18 * H, JAN + 30 * H, "home", "INFERRED_HOME"),
                visit(JAN + 31 * H, JAN + 32 * H, "cafe"),
                visit(JAN + 32 * H, JAN + 33 * H, "cafe-nested", "UNKNOWN", 1),
                visit(FEB, FEB + H, "cafe"),
                visit(FEB + 2 * H, FEB + 3 * H, "museum"),
            ),
            activities = listOf(
                act(JAN + 8 * H, JAN + 8 * H + 1800, "WALKING", 1000.0),
                act(JAN + 8 * H + 1800, JAN + 9 * H, "IN_SUBWAY", 5000.0),
                act(JAN + 17 * H, JAN + 18 * H, "IN_PASSENGER_VEHICLE", 6000.0),
                act(FEB + 4320, FEB + 6480, "FLYING", 500000.0),
            ),
            places = mapOf(
                "home" to place(Role.HOME, "Boston", "US", 2, JAN, JAN + 18 * H),
                "work" to place(Role.WORK, "Boston", "US", 1, JAN, JAN),
                "cafe" to place(null, "Boston", "US", 2, JAN + 31 * H, FEB, "Cafe"),
                "museum" to place(null, "Paris", "FR", 1, FEB + 2 * H, FEB + 2 * H, "Louvre"),
                "old" to place(null, "Boston", "US", 5, JAN - 200 * D, JAN - 80 * D, "Gym"),
            ),
            daily = listOf(
                daily("2025-01-01", mapOf("WALKING" to 1.0), 3, listOf("Boston"), hoursWork = 8.0, hoursOther = 1.0, maxDistFromHomeKm = 5.0),
                daily("2025-02-01", mapOf("FLYING" to 500.0), 2, listOf("Paris"), hoursWork = 0.0, hoursOther = 2.0, maxDistFromHomeKm = 5000.0),
            ),
            home = LatLng(0.0, 0.0), range = JAN..MAR,
        )
    }

    @Test fun `homeFacts totals the export, excludes home work from third places, and rolls up years`() {
        val f = homeFacts(data)
        assertEquals(512.0, f.totalKm, 1e-9)
        assertEquals("FLYING" to 500.0, f.kmByMode[0])
        assertEquals(5, f.places)
        assertEquals(2, f.countries)
        assertEquals("Boston", f.topCities[0])
        assertEquals(listOf("Gym", "Cafe", "Louvre"), f.thirdPlaces.map { it.name })
        assertEquals(DayPlaces("2025-01-01", 3), f.records.busiestDay)
        assertEquals(listOf(YearRow(2025, 512.0, 4, 0, 1)), f.years)
    }

    @Test fun `stats rolls km by month, classifies days, fills the rhythm grid and ranks cities`() {
        val s = stats(data)
        assertEquals(listOf("2025-01" to 1.0, "2025-02" to 500.0), s.months.map { it.key to it.total })
        assertEquals(mapOf(DayType.AWAY to 1, DayType.WORK to 1, DayType.OUT to 0, DayType.HOME to 0), s.dayTypes)
        assertEquals(DayType.HOME, dayType(daily("x", maxDistFromHomeKm = 1.0, hoursWork = 0.0, hoursOther = 0.5)))
        // work visit 09:00–17:00 on Wed 2025-01-01 credits 8 full hours to Wednesday
        assertEquals(List(8) { 1.0 }, s.rhythm[2].subList(9, 17))
        assertEquals(0.0, s.rhythm[2][8])
        assertEquals(listOf("Boston", "US", 5, 3), s.cities[0].let { listOf(it.city, it.country, it.visits, it.places) })
        assertEquals(listOf("Paris", 1, 1, 1.0), s.cities[1].let { listOf(it.city, it.visits, it.places, it.hours) })
    }
}
