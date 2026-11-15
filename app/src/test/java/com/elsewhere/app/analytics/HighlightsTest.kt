package com.elsewhere.app.analytics

import com.elsewhere.app.OnThisDay
import com.elsewhere.app.OnThisDay.BOS
import com.elsewhere.app.OnThisDay.KYO
import com.elsewhere.app.OnThisDay.at
import com.elsewhere.app.OnThisDay.day
import com.elsewhere.app.OnThisDay.noHome
import com.elsewhere.app.OnThisDay.place
import com.elsewhere.app.OnThisDay.trip
import com.elsewhere.app.OnThisDay.v
import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

/** Twin of web/src/analytics/highlights.test.ts: same fixture, same expected values. */
class HighlightsTest {
    private val data = OnThisDay.data()

    @Nested inner class PickingTheHighlight {
        @Test fun `scores a day away, in a trip, with new places, distance and a first city`() {
            // 2022-11-15 Paris: +50 away, +30 trip, +20 two new places, +11 (5500/500), +15 first time in Paris.
            val h = highlightFor(data, "2025-11-15")!!
            assertEquals("2022-11-15", h.date)
            assertEquals(126, h.score)
            assertEquals(3, h.yearsAgo)
            assertEquals(Scope.DAY, h.scope)
        }
        @Test fun `penalises an all-home-or-work day below zero so it never wins`() {
            assertEquals(-40, highlightOn(data, "2024-11-15", 1).score)
            assertEquals("2023-11-14", highlightFor(data, "2025-11-14")!!.date)
        }
        @Test fun `caps the distance bonus at 20`() {
            // Kyoto 11-14: +50 +30 +0 new +min(20, 22) = 100.
            assertEquals(100, highlightOn(data, "2023-11-14", 2).score)
        }
        @Test fun `breaks ties in favour of the oldest year`() {
            // Two Kyoto-identical days one year apart: same visits, same daily. Copy 2023-11-13 into 2024-11-13.
            val shift = at(2024, 11, 13) - at(2023, 11, 13)
            val d = data.copy(
                visits = (data.visits + v(at(2023, 11, 13, 0) + shift, at(2023, 11, 13, 23) + shift, "kyoto-hotel", KYO)).sortedBy { it.start },
                daily = (data.daily + day("2024-11-13", maxDistFromHomeKm = 10_800.0, cities = listOf("Kyoto"), hoursOther = 20.0)).sortedBy { it.date },
                trips = data.trips + trip(at(2024, 11, 13), at(2024, 11, 14), 1, listOf("Kyoto"), 10_800.0),
            )
            val h = highlightFor(d, "2025-11-13")!!
            assertEquals(highlightOn(d, "2023-11-13", 2).score, highlightOn(d, "2024-11-13", 1).score)
            assertEquals("2023-11-13", h.date)
        }
        @Test fun `falls back to 28 Feb when today is 29 Feb`() {
            val h = highlightFor(data, "2028-02-29")!!
            assertEquals("2023-02-28", h.date)
            assertEquals("New York", h.city)
        }
        @Test fun `widens to plus or minus 3 days and labels the result this week`() {
            val h = highlightFor(data, "2025-11-20")!!
            assertEquals(Scope.WEEK, h.scope)
            assertTrue(h.date in listOf("2023-11-17", "2023-11-18"))
        }
        @Test fun `returns null on a quiet day`() {
            assertNull(highlightFor(data, "2025-07-20"))
        }
        @Test fun `only looks at past years, never the current one`() {
            assertNull(highlightFor(data, "2025-05-03"))
            assertEquals("2025-05-03", highlightFor(data, "2026-05-03")!!.date)
        }
    }

    @Nested inner class HeadlineVerb {
        @Test fun `woke up in when the first visit is a night stay`() {
            val h = highlightOn(data, "2023-11-14", 2)
            assertEquals(Verb.WOKE_UP_IN, h.verb); assertEquals("Kyoto", h.city)
        }
        @Test fun `landed in when the first leg of the day is a flight`() {
            val h = highlightOn(data, "2022-11-15", 3)
            assertEquals(Verb.LANDED_IN, h.verb); assertEquals("Paris", h.city)
        }
        @Test fun `arrived in when the first visit is a new city after sleeping elsewhere`() {
            // Woke in Kyoto, took the train: the day belongs to Kyoto (first visit) and reads "woke up in".
            assertEquals(Verb.WOKE_UP_IN, highlightOn(data, "2023-11-17", 2).verb)
            // Drop the hotel's last morning so the day starts with the train and the Osaka hotel.
            val d = data.copy(visits = data.visits.map { if (it.placeId == "kyoto-hotel" && it.start == at(2023, 11, 11, 23)) it.copy(end = at(2023, 11, 16, 23)) else it })
            assertEquals(Verb.ARRIVED_IN, highlightOn(d, "2023-11-17", 2).verb)
        }
        @Test fun `were in otherwise - the day opens at a late stop in the city you then slept in`() {
            val visits = data.visits.flatMap { x ->
                if (x.placeId == "kyoto-hotel" && x.start == at(2023, 11, 11, 23))
                    listOf(x.copy(end = at(2023, 11, 14, 23)), v(at(2023, 11, 14, 23, 30), at(2023, 11, 15, 1), "kyoto-bar", KYO), x.copy(id = 99, start = at(2023, 11, 15, 1, 30)))
                else listOf(x)
            }.sortedBy { it.start }
            val d = data.copy(visits = visits, places = data.places + ("kyoto-bar" to place(KYO, "Kyoto", "JP", "Bar")))
            val h = highlightOn(d, "2023-11-15", 2)
            assertEquals("kyoto-bar", h.firstVisit.placeId)
            assertEquals(Verb.WERE_IN, h.verb)
        }
        @Test fun `prints coordinates when the city is unknown`() {
            val d = data.copy(places = data.places + ("nyc-hotel" to data.places.getValue("nyc-hotel").copy(city = null)))
            val h = highlightOn(d, "2023-02-28", 2)
            assertFalse(h.cityKnown)
            assertEquals("40.71°, -74.01°", h.city)
        }
    }

    @Nested inner class Tier1Facts {
        @Test fun `picks the first three that apply, in priority order`() {
            val h = highlightOn(data, "2023-11-14", 2)
            assertEquals(listOf(FactId.TRIP, FactId.KM, FactId.WEEK), h.facts.map { it.id })
            assertEquals("3 nights into a 7-night trip", h.facts[0].label)
            assertEquals("10,800 km from home", h.facts[1].label)
            assertEquals(7, h.facts[2].strip!!.size)
        }
        @Test fun `includes first-city, new places and transit when they apply`() {
            val h = highlightOn(data, "2022-11-15", 3)
            assertEquals(listOf(FactId.TRIP, FactId.KM, FactId.FIRST), h.facts.map { it.id })
            val h2 = highlightOn(data.copy(trips = emptyList()), "2022-11-15", 3)
            assertEquals(listOf(FactId.KM, FactId.FIRST, FactId.NEW), h2.facts.map { it.id })
        }
        @Test fun `hides distance facts when no home is set`() {
            assertFalse(FactId.KM in highlightOn(data.noHome(), "2023-11-14", 2).facts.map { it.id })
        }
        @Test fun `every fact is a door with a range and place ids`() {
            val h = highlightOn(data, "2023-11-14", 2)
            for (f in h.facts) { assertTrue(f.range.first < f.range.last); assertNotNull(f.placeIds) }
            assertEquals(at(2023, 11, 14)..at(2023, 11, 15), h.range)
            assertEquals(at(2023, 11, 11, 23), h.playhead)
            assertEquals(listOf("kyoto-hotel"), h.placeIds)
        }
    }

    @Nested inner class EmptyStates {
        @Test fun `quiet day names the nearest highlight by calendar distance`() {
            assertTrue(highlightState(data, "2025-11-20") is HighlightState.Found)
            val q = highlightState(data, "2025-07-20") as HighlightState.Quiet
            assertEquals("2025-05-03", q.nearest?.date); assertEquals("Kyoto", q.nearest?.city)
        }
        @Test fun `record shorter than a year shows the top-scoring day instead`() {
            val d = data.copy(meta = data.meta.copy(range = at(2023, 3, 3)..at(2023, 5, 1)))
            val s = highlightState(d, "2023-04-10") as HighlightState.Short
            assertEquals("2023-03-03", s.starts); assertEquals("2022-11-15", s.highlight.date)
        }
        @Test fun `an empty record has nothing to say`() {
            assertEquals(HighlightState.None, highlightState(data.copy(visits = emptyList()), "2025-11-15"))
        }
    }

    @Nested inner class FollowUpsOnKyoto {
        private val kyoto = highlightOn(data, "2023-11-14", 2)
        private val paris = highlightOn(data, "2022-11-15", 3)

        @Test fun `offers only the questions that have an answer`() {
            val ids = followUps(data, kyoto).map { it.id }
            assertEquals(listOf(FollowUpId.STAY, FollowUpId.DISTANCE, FollowUpId.TRIP, FollowUpId.ROUTE, FollowUpId.COMPARE, FollowUpId.DID, FollowUpId.BACK, FollowUpId.FARTHEST), ids)
            assertFalse(FollowUpId.LAST_YEAR in ids) // 2022-11-14 was an ordinary day at home
            assertTrue(FollowUpId.LAST_YEAR in followUps(data, highlightOn(data, "2023-11-15", 2)).map { it.id })
        }
        @Test fun `stayLength is the contiguous run of nights in the city around the day`() {
            val s = stayLength(data, kyoto)!!
            assertEquals(Triple(6, "2023-11-12", "2023-11-17"), Triple(s.nights, s.from, s.to))
            assertEquals(6, s.strip.size)
            assertEquals("6 nights", s.title)
            assertEquals("12–17 Nov 2023", s.detail)
            assertEquals(listOf("kyoto-hotel"), s.placeIds)
            assertEquals(2, stayLength(data, paris)!!.nights)
        }
        @Test fun `distanceFromHome names the leg that got you there`() {
            val d = distanceFromHome(data, kyoto)!!
            assertEquals(10_989, d.km.roundToInt())
            assertEquals(LegInfo("FLYING", "Boston", "Kyoto", 14, at(2023, 11, 11, 8)..at(2023, 11, 11, 22)), d.leg)
            assertEquals("10,989 km", d.title)
            assertEquals("flew Boston → Kyoto, 14 h", d.detail)
            assertNull(distanceFromHome(data.noHome(), kyoto))
        }
        @Test fun `tripSetlist lists the cities with nights, marking firsts`() {
            val t = tripSetlist(data, kyoto)!!
            assertEquals(listOf(Triple("Kyoto", 6, true), Triple("Osaka", 1, true)), t.cities.map { Triple(it.city, it.nights, it.first) })
            assertTrue(t.cities[1].range.first < t.cities[1].range.last)
            assertEquals(at(2023, 11, 11, 8)..at(2023, 11, 19, 2), t.range)
            assertNull(tripSetlist(data, highlightOn(data, "2023-02-28", 2)))
        }
        @Test fun `routeRange covers the trip and counts its legs`() {
            val r = routeRange(data, kyoto)!!
            assertEquals(at(2023, 11, 11, 8)..at(2023, 11, 19, 2), r.range)
            assertEquals(3, r.legs)
            assertEquals(21_645.0, r.km, 0.01)
        }
        @Test fun `compareYears gives one column per year with the city and distance`() {
            val c = compareYears(data, kyoto)!!
            assertEquals(
                listOf(YearCol(2022, "Boston", 5.0, false), YearCol(2023, "Kyoto", 10_800.0, true), YearCol(2024, "Boston", 5.0, false)),
                c.years,
            )
            val d = data.copy(daily = data.daily.filter { it.date == "2023-02-28" })
            assertNull(compareYears(d, highlightOn(d, "2023-02-28", 2)))
        }
        @Test fun `whatDidIDo lists the stops of the day with durations`() {
            val w = whatDidIDo(data, highlightOn(data, "2023-11-15", 2))!!
            assertEquals(listOf(Triple("Kyoto Hotel", "lodging", 24), Triple("Kinkaku-ji", "temple", 3)), w.stops.map { Triple(it.name, it.type, it.hours.roundToInt()) })
            assertEquals(listOf("kyoto-hotel", "kinkakuji"), w.placeIds)
        }
        @Test fun `haveIBeenBack counts returns after the stay ended and dates the last`() {
            // 15 Nov (temple) is still the same stay; May 2025 is the one return.
            val b = haveIBeenBack(data, kyoto)!!
            assertEquals(listOf(1, "2025-05-04", "Once", "last 4 May 2025"), listOf(b.times, b.last, b.title, b.detail))
            val p = haveIBeenBack(data, paris)!!
            assertEquals(listOf(0, null, "Never", "2 years and counting"), listOf(p.times, p.last, p.title, p.detail))
        }
        @Test fun `farthestEver compares against the record`() {
            val f = farthestEver(data, kyoto)!!
            assertEquals(listOf(false, "2022-03-10", 16_200.0, "Sydney", "No"), listOf(f.isFarthest, f.date, f.km, f.city, f.title))
            val s = farthestEver(data, highlightOn(data, "2022-03-10", 3))!!
            assertTrue(s.isFarthest); assertEquals("Yes", s.title)
            assertNull(farthestEver(data.noHome(), kyoto))
        }
        @Test fun `thisTimeLastYear is another highlight, and chains`() {
            val h = thisTimeLastYear(data, highlightOn(data, "2023-11-15", 2))!!
            assertEquals("2022-11-15", h.highlight.date)
            assertEquals(3, h.highlight.yearsAgo)
            assertNull(thisTimeLastYear(data, kyoto))
        }
        @Test fun `chaining stops at depth 4`() {
            assertTrue(followUps(data, kyoto, MAX_DEPTH - 1).isNotEmpty())
            assertEquals(emptyList<FollowUp>(), followUps(data, kyoto, MAX_DEPTH))
        }
        @Test fun `excluded questions are left out of the menu`() {
            assertFalse(FollowUpId.STAY in followUps(data, kyoto, 1, setOf(FollowUpId.STAY)).map { it.id })
        }
        @Test fun `every answer is a door`() {
            for (f in followUps(data, kyoto)) {
                assertTrue(f.answer.range.first <= f.answer.range.last)
                assertNotNull(f.answer.placeIds)
            }
        }
    }

    @Nested inner class HotPath {
        @Test fun `indexes a record once and answers in sub-linear time per query`() {
            // 100k top-level visits over three years, a few hundred a day, with a flight every 30 days.
            val start = at(2022, 1, 1)
            val visits = ArrayList<Visit>(); val daily = ArrayList<Daily>(); val trips = ArrayList<Trip>()
            val places = HashMap<String, Place>(); places["home"] = place(BOS, "Boston", "US", null, com.elsewhere.app.data.Role.HOME)
            for (i in 0 until 100_000) {
                val t = start + i * 950L
                val far = (i / 2700) % 30 == 29
                val pid = if (far) "far${i / 2700}" else "p${i % 500}"
                places.getOrPut(pid) { place(if (far) KYO else BOS, if (far) "City $pid" else "Boston", if (far) "JP" else "US", pid) }
                visits.add(v(t, t + 900, pid, if (far) KYO else BOS))
            }
            val days = ((visits.last().end - start + 86399) / 86400).toInt()
            for (d in 0 until days) {
                val far = ((d * 86400L) / (2700L * 950)).toInt() % 30 == 29
                daily.add(day(localDay(start + d * 86400L, 0), maxDistFromHomeKm = if (far) 10_800.0 else 3.0, hoursOther = 20.0, nNewPlaces = 2))
                if (far && d % 7 == 0) trips.add(trip(start + d * 86400L, start + (d + 7) * 86400L, 7, listOf("Kyoto"), 10_800.0))
            }
            val big = data.copy(visits = visits, activities = emptyList<Activity>(), daily = daily, trips = trips, places = places, meta = data.meta.copy(range = start..visits.last().end))
            val t0 = System.nanoTime()
            val ix = indexOf(big)
            val built = (System.nanoTime() - t0) / 1e6
            assertSame(ix, indexOf(big))
            val t1 = System.nanoTime()
            var n = 0
            for (m in 1..12) for (d in 1..28 step 3) {
                val h = highlightFor(big, "2026-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}")
                if (h != null) { followUps(big, h); n++ }
            }
            val queried = (System.nanoTime() - t1) / 1e6
            assertTrue(n > 0)
            assertTrue(built < 1500, "index took $built ms")
            assertTrue(queried < 1500, "queries took $queried ms")
        }
    }
}
