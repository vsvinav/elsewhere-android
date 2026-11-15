package com.elsewhere.app.analytics

import com.elsewhere.app.Fx
import com.elsewhere.app.Fx.D
import com.elsewhere.app.Fx.H
import com.elsewhere.app.Fx.act
import com.elsewhere.app.Fx.daily
import com.elsewhere.app.Fx.place
import com.elsewhere.app.Fx.visit
import com.elsewhere.app.data.Activity
import com.elsewhere.app.data.Daily
import com.elsewhere.app.data.Home
import com.elsewhere.app.data.LatLng
import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Record
import com.elsewhere.app.data.Role
import com.elsewhere.app.data.Trip
import com.elsewhere.app.data.Visit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Twin of web/src/analytics/highlights.test.ts: one record, many dates, every door checked. */
class HighlightsTest {
    companion object {
        val boston = LatLng(42.36, -71.06)
        val kyoto = LatLng(35.01, 135.77)
        val osaka = LatLng(34.69, 135.5)
        val lisbon = LatLng(38.72, -9.14)
        val montreal = LatLng(45.5, -73.57)
        val providence = LatLng(41.82, -71.41)
        val portland = LatLng(43.66, -70.26)
        val nyc = LatLng(40.71, -74.0)
        val chicago = LatLng(41.88, -87.63)

        fun d(s: String): Long = LocalDate.parse(s).toEpochDay() * D
        fun home(day: String) = visit(d(day) + 18 * H, d(day) + 32 * H, "home", lat = boston.lat, lng = boston.lng)
        fun homeDay(day: String) = daily(day, mapOf("WALKING" to 2.0), 2, listOf("Boston"), hoursHome = 14.0, hoursWork = 8.0, maxDistFromHomeKm = 3.0)

        // Kyoto trip 12–17 Nov 2022: flew out on the 12th, three nights in Kyoto, one in Osaka.
        val kyotoTrip = Trip(d("2022-11-12"), d("2022-11-17") + 12 * H, 5, 10_800.0, mapOf("FLYING" to 21_600.0, "WALKING" to 40.0), listOf("Kyoto", "Osaka"), null)
        // Lisbon trip 19–23 Aug 2023: overnight flight lands the morning of the 20th.
        val lisbonTrip = Trip(d("2023-08-19") + 20 * H, d("2023-08-23") + 12 * H, 3, 5_180.0, mapOf("FLYING" to 10_360.0), listOf("Lisbon"), null)

        val visits: List<Visit> = listOf(
            // Portland first seen on a different date, so the two 4 July days tie exactly.
            visit(d("2019-09-01") + 10 * H, d("2019-09-01") + 14 * H, "portland-pier", lat = portland.lat, lng = portland.lng),
            // Providence, 01:00 → 11:00 on the day: the night is spent there, but the stay began on the day itself.
            home("2021-03-03"),
            visit(d("2021-03-05") + 1 * H, d("2021-03-05") + 11 * H, "providence-inn", lat = providence.lat, lng = providence.lng),
            visit(d("2021-07-04") + 10 * H, d("2021-07-04") + 15 * H, "portland-pier", lat = portland.lat, lng = portland.lng),
            visit(d("2021-08-20") + 9 * H, d("2021-08-20") + 15 * H, "chicago-loop", lat = chicago.lat, lng = chicago.lng),
            home("2022-11-10"),
            visit(d("2022-11-13") + 8 * H, d("2022-11-16") + 10 * H, "kyoto-hotel", lat = kyoto.lat, lng = kyoto.lng),
            visit(d("2022-11-15") + 12 * H, d("2022-11-15") + 14 * H, "kyoto-temple", lat = kyoto.lat + 0.01, lng = kyoto.lng),
            visit(d("2022-11-16") + 14 * H, d("2022-11-17") + 10 * H, "osaka-hotel", lat = osaka.lat, lng = osaka.lng),
            visit(d("2023-02-28") + 9 * H, d("2023-02-28") + 17 * H, "nyc-gallery", lat = nyc.lat, lng = nyc.lng),
            visit(d("2023-07-04") + 10 * H, d("2023-07-04") + 15 * H, "portland-pier", lat = portland.lat, lng = portland.lng),
            visit(d("2023-08-20") + 8 * H, d("2023-08-23") + 10 * H, "lisbon-hotel", lat = lisbon.lat, lng = lisbon.lng),
            home("2023-11-14"),
            visit(d("2023-11-15") + 9 * H, d("2023-11-15") + 17 * H, "work", lat = 42.35, lng = -71.05),
            visit(d("2024-04-02") + 9 * H, d("2024-04-02") + 17 * H, "kyoto-temple", lat = kyoto.lat + 0.01, lng = kyoto.lng),
            // Montreal: home stay ends before 03:00, so the morning has no bed on record, then a café in a new city.
            visit(d("2024-06-09") + 18 * H, d("2024-06-10") + 2 * H, "home", lat = boston.lat, lng = boston.lng),
            visit(d("2024-06-10") + 10 * H, d("2024-06-10") + 12 * H, "montreal-cafe", lat = montreal.lat, lng = montreal.lng),
        ).sortedBy { it.start }

        val activities: List<Activity> = listOf(
            act(d("2021-03-04") + 23 * H, d("2021-03-05") + 50 * 60, "IN_PASSENGER_VEHICLE", 80_000.0, boston.lng, boston.lat, providence.lng, providence.lat),
            act(d("2022-11-12") + 10 * H, d("2022-11-13") + 6 * H, "FLYING", 10_800_000.0, boston.lng, boston.lat, kyoto.lng, kyoto.lat),
            act(d("2022-11-16") + 11 * H, d("2022-11-16") + 12 * H, "IN_TRAIN", 45_000.0, kyoto.lng, kyoto.lat, osaka.lng, osaka.lat),
            act(d("2022-11-17") + 12 * H, d("2022-11-18") + 2 * H, "FLYING", 10_800_000.0, osaka.lng, osaka.lat, boston.lng, boston.lat),
            act(d("2023-08-19") + 22 * H, d("2023-08-20") + 6 * H, "FLYING", 5_180_000.0, boston.lng, boston.lat, lisbon.lng, lisbon.lat),
            act(d("2024-06-10") + 4 * H, d("2024-06-10") + 9 * H, "IN_PASSENGER_VEHICLE", 500_000.0, boston.lng, boston.lat, montreal.lng, montreal.lat),
        )

        val places: Map<String, Place> = mapOf(
            "home" to place(Role.HOME, "Boston", "US", 400, name = "Home", lat = boston.lat, lng = boston.lng),
            "work" to place(Role.WORK, "Boston", "US", 200, name = "The Office", lat = 42.35, lng = -71.05),
            "portland-pier" to place(null, "Portland", "US", 3, name = "Maine State Pier", lat = portland.lat, lng = portland.lng, primaryType = "tourist_attraction"),
            "providence-inn" to place(null, "Providence", "US", 1, name = "Dean Hotel", lat = providence.lat, lng = providence.lng, primaryType = "lodging"),
            "chicago-loop" to place(null, "Chicago", "US", 1, name = "The Loop", lat = chicago.lat, lng = chicago.lng),
            "kyoto-hotel" to place(null, "Kyoto", "JP", 1, name = "Hotel Kanra", lat = kyoto.lat, lng = kyoto.lng, primaryType = "lodging"),
            "kyoto-temple" to place(null, "Kyoto", "JP", 2, name = "Kiyomizu-dera", lat = kyoto.lat + 0.01, lng = kyoto.lng, primaryType = "place_of_worship"),
            "osaka-hotel" to place(null, "Osaka", "JP", 1, name = "Cross Hotel", lat = osaka.lat, lng = osaka.lng, primaryType = "lodging"),
            "nyc-gallery" to place(null, "New York", "US", 1, name = "The Met", lat = nyc.lat, lng = nyc.lng, primaryType = "museum"),
            "lisbon-hotel" to place(null, "Lisbon", "PT", 1, name = "Casa do Bairro", lat = lisbon.lat, lng = lisbon.lng, primaryType = "lodging"),
            "montreal-cafe" to place(null, "Montreal", "CA", 1, name = "Olimpico", lat = montreal.lat, lng = montreal.lng, primaryType = "cafe"),
        )

        val dailies: List<Daily> = listOf(
            daily("2019-09-01", nPlaces = 1, cities = listOf("Portland"), hoursOther = 4.0, maxDistFromHomeKm = 150.0),
            daily("2021-03-05", nPlaces = 1, cities = listOf("Providence"), hoursOther = 10.0, maxDistFromHomeKm = 80.0).copy(nNewPlaces = 1),
            daily("2021-07-04", nPlaces = 1, cities = listOf("Portland"), hoursOther = 5.0, maxDistFromHomeKm = 150.0),
            daily("2021-08-20", nPlaces = 1, cities = listOf("Chicago"), hoursOther = 6.0, maxDistFromHomeKm = 1_370.0).copy(nNewPlaces = 1),
            daily("2022-11-13", nPlaces = 1, cities = listOf("Kyoto"), hoursOther = 16.0, maxDistFromHomeKm = 10_800.0).copy(nNewPlaces = 1),
            daily("2022-11-14", nPlaces = 1, cities = listOf("Kyoto"), hoursOther = 24.0, maxDistFromHomeKm = 10_800.0),
            daily("2022-11-15", nPlaces = 2, cities = listOf("Kyoto"), hoursOther = 22.0, hoursTransit = 1.4, maxDistFromHomeKm = 10_800.0).copy(nNewPlaces = 2),
            daily("2022-11-16", nPlaces = 2, cities = listOf("Kyoto", "Osaka"), hoursOther = 20.0, hoursTransit = 1.0, maxDistFromHomeKm = 10_800.0).copy(nNewPlaces = 1),
            daily("2022-11-17", nPlaces = 1, cities = listOf("Osaka"), hoursOther = 10.0, hoursTransit = 12.0, maxDistFromHomeKm = 10_800.0),
            daily("2023-02-28", nPlaces = 1, cities = listOf("New York"), hoursOther = 8.0, maxDistFromHomeKm = 306.0).copy(nNewPlaces = 1),
            daily("2023-07-04", nPlaces = 1, cities = listOf("Portland"), hoursOther = 5.0, maxDistFromHomeKm = 150.0),
            daily("2023-08-20", nPlaces = 1, cities = listOf("Lisbon"), hoursOther = 16.0, hoursTransit = 6.0, maxDistFromHomeKm = 5_180.0).copy(nNewPlaces = 1),
            homeDay("2023-11-15"),
            daily("2024-04-02", nPlaces = 1, cities = listOf("Kyoto"), hoursOther = 8.0, maxDistFromHomeKm = 10_800.0),
            daily("2024-06-10", nPlaces = 1, cities = listOf("Montreal"), hoursOther = 2.0, hoursTransit = 5.0, maxDistFromHomeKm = 400.0).copy(nNewPlaces = 1),
        )

        val range = d("2019-01-01")..(d("2025-11-01") - 1)
        val data: Record = Fx.record(visits, activities, places, dailies, listOf(kyotoTrip, lisbonTrip), home = boston, range = range)
            .let { it.copy(profile = it.profile.copy(homes = listOf(Home(boston.lat, boston.lng, "Boston", "US", range.first, null)))) }

        val today = LocalDate.of(2025, 11, 15)
        val kyotoDay by lazy { highlightFor(data, today)!! }
        fun ask(h: Highlight, q: Question): Answer = followUps(data, h).first { it.question == q }.answer
    }

    // --- picking the highlight ---

    @Test fun `picks the best-scoring past year for today's date`() {
        val h = kyotoDay
        assertEquals("2022-11-15", h.date)
        assertEquals(3, h.yearsAgo)
        assertFalse(h.thisWeek)
        assertEquals("Kyoto", h.city)
    }

    @Test fun `scores away, trip, new places, distance and first visit, and penalises a home or work day`() {
        // Kyoto 15 Nov 2022: away 50 + trip 30 + 2 new places 20 + min(20, 10800/500) 20; the city was first seen on the 13th.
        assertEquals(120, kyotoDay.score)
        // The 13th is the first Kyoto day: same, plus 15, with one new place.
        assertEquals(50 + 30 + 10 + 20 + 15, scoreOf(data, LocalDate.of(2022, 11, 13)))
        // A home-and-work Boston day goes negative and never surfaces.
        assertEquals(-40, scoreOf(data, LocalDate.of(2023, 11, 15)))
    }

    @Test fun `ties go to the oldest year`() {
        val h = highlightFor(data, LocalDate.of(2025, 7, 4))!!
        assertEquals("2021-07-04", h.date)
        assertEquals(50, h.score)
        assertEquals(4, h.yearsAgo)
    }

    @Test fun `29 February falls back to 28 February in ordinary years`() {
        val h = highlightFor(data, LocalDate.of(2028, 2, 29))!!
        assertEquals("2023-02-28", h.date)
        assertEquals("New York", h.city)
    }

    @Test fun `widens to plus or minus three days and says so`() {
        val h = highlightFor(data, LocalDate.of(2025, 11, 20))!!
        assertEquals("2022-11-17", h.date)
        assertTrue(h.thisWeek)
        assertEquals("Osaka", h.city)
        assertTrue(h.headline.startsWith("This week, 3 years ago"))
    }

    @Test fun `a quiet day returns null and names the nearest card`() {
        val day = LocalDate.of(2025, 2, 10)
        assertNull(highlightFor(data, day))
        val r = highlightsFor(data, day) as HighlightResult.Quiet
        // 28 Feb 2023 is 18 days off; 5 Mar 2021 is 23.
        assertEquals("2023-02-28", r.nearest?.date)
        assertEquals("New York", r.nearest?.city)
    }

    @Test fun `a record shorter than a year shows its best day instead`() {
        val short = Fx.record(
            visits = listOf(visit(d("2025-06-01") + 9 * H, d("2025-06-01") + 17 * H, "nyc-gallery", lat = nyc.lat, lng = nyc.lng), home("2025-06-02")),
            places = places, daily = listOf(daily("2025-06-01", nPlaces = 1, cities = listOf("New York"), hoursOther = 8.0, maxDistFromHomeKm = 306.0).copy(nNewPlaces = 1), homeDay("2025-06-03")),
            home = boston, range = d("2025-03-03")..(d("2025-11-10") - 1),
        )
        assertNull(highlightFor(short, today))
        val r = highlightsFor(short, today) as HighlightResult.Short
        assertEquals("2025-03-03", r.start)
        assertEquals("2025-06-01", r.best?.date)
        assertEquals(0, r.best?.yearsAgo)
    }

    @Test fun `only past years are candidates`() {
        // 2 April 2024 is Kyoto again; asked on 2 April 2024 itself it must not count.
        val h = highlightFor(data, LocalDate.of(2024, 4, 2))
        assertNull(h)
    }

    // --- headline verbs ---

    @Test fun `woke up in when the first visit is a night stay that began before the day`() {
        assertEquals(Verb.WOKE_UP, kyotoDay.verb)
        assertEquals("On this day, 3 years ago — you woke up in Kyoto.", kyotoDay.headline)
    }

    @Test fun `landed in when the first leg of the day is a flight`() {
        val h = highlightFor(data, LocalDate.of(2025, 8, 20))!!
        assertEquals("2023-08-20", h.date)
        assertEquals(Verb.LANDED, h.verb)
        assertEquals("On this day, 2 years ago — you landed in Lisbon.", h.headline)
    }

    @Test fun `arrived in when the first visit is not where the previous night was spent`() {
        val h = highlightFor(data, LocalDate.of(2025, 6, 10))!!
        assertEquals(Verb.ARRIVED, h.verb)
        assertEquals("On this day, last year — you arrived in Montreal.", h.headline)
    }

    @Test fun `were in otherwise`() {
        val h = highlightFor(data, LocalDate.of(2025, 3, 5))!!
        assertEquals("Providence", h.city)
        assertEquals(Verb.WERE_IN, h.verb)
    }

    @Test fun `a day without place names prints coordinates as the city`() {
        val nameless = data.copy(places = data.places.mapValues { (_, p) -> p.copy(city = null, name = null) })
        val h = highlightFor(nameless, today)!!
        assertFalse(h.named)
        assertEquals("35.01°N 135.77°E", h.city)
    }

    // --- tier 1 facts ---

    @Test fun `facts come in priority order, three at most, each carrying its day range`() {
        val f = kyotoDay.facts
        // Kyoto was first seen on the 13th, so "first time" does not apply on the 15th and new places take the slot.
        assertEquals(listOf(FactKind.TRIP, FactKind.DISTANCE, FactKind.NEW_PLACES), f.map { it.kind })
        assertEquals("3 nights into a 5-night trip", f[0].label)
        assertEquals("10,800 km from home", f[1].label)
        assertEquals("2 new places that day", f[2].label)
        val first = highlightFor(data, LocalDate.of(2025, 11, 13))!!
        assertEquals("First time in Kyoto", first.facts.first { it.kind == FactKind.FIRST }.label)
        assertEquals(kyotoDay.range, f[1].range)
        assertEquals(kyotoTrip.start..kyotoTrip.end, f[0].range)
        assertEquals(listOf("kyoto-hotel", "kyoto-temple"), kyotoDay.placeIds)
    }

    @Test fun `the first visit of the day is the playhead and the range is that local day`() {
        assertEquals(d("2022-11-15"), kyotoDay.range.first)
        assertEquals(d("2022-11-16") - 1, kyotoDay.range.last)
        assertEquals(d("2022-11-13") + 8 * H, kyotoDay.playhead)
    }

    @Test fun `without a home set the distance facts and questions are hidden`() {
        val homeless = data.copy(profile = data.profile.copy(home = LatLng(0.0, 0.0), homes = emptyList()))
        val h = highlightFor(homeless, today)!!
        assertTrue(h.facts.none { it.kind == FactKind.DISTANCE })
        val qs = followUps(homeless, h).map { it.question }
        assertFalse(Question.DISTANCE in qs)
        assertFalse(Question.FARTHEST in qs)
    }

    @Test fun `a thinner day falls through to new places, transit and the week strip`() {
        val h = highlightFor(data, LocalDate.of(2025, 6, 10))!!
        assertEquals(listOf(FactKind.DISTANCE, FactKind.FIRST, FactKind.NEW_PLACES), h.facts.map { it.kind })
        val p = highlightFor(data, LocalDate.of(2025, 3, 5))!!
        assertEquals(listOf(FactKind.DISTANCE, FactKind.FIRST, FactKind.NEW_PLACES), p.facts.map { it.kind })
        val week = weekStrip(data, p.date)
        assertEquals(7, week.size)
        assertEquals("2021-03-02", week.first().date)
        assertEquals(NightKind.NEW, week[3].kind)
    }

    // --- follow-ups ---

    @Test fun `only answerable questions are offered`() {
        val qs = followUps(data, kyotoDay).map { it.question }
        assertEquals(listOf(Question.STAY, Question.DISTANCE, Question.TRIP, Question.ROUTE, Question.COMPARE, Question.DID, Question.BACK, Question.FARTHEST), qs)
        // Montreal: no trip, no stay, nothing a year before.
        val m = followUps(data, highlightFor(data, LocalDate.of(2025, 6, 10))!!).map { it.question }
        assertEquals(listOf(Question.DISTANCE, Question.DID, Question.BACK, Question.FARTHEST), m)
    }

    @Test fun `how long did I stay`() {
        val a = ask(kyotoDay, Question.STAY) as Answer.Stay
        assertEquals(3, a.nights)
        assertEquals("2022-11-14" to "2022-11-16", a.from to a.to)
        assertEquals(3, a.strip.size)
        assertTrue(a.strip.all { it.city == "Kyoto" })
        assertEquals(listOf("kyoto-hotel", "kyoto-temple"), a.placeIds)
        assertEquals("3 nights · 14–16 Nov 2022", a.title)
    }

    @Test fun `how far from home, with the leg that got you there`() {
        val a = ask(kyotoDay, Question.DISTANCE) as Answer.Distance
        assertEquals(distanceKm(boston, LatLng(35.015, 135.77)), a.km, 1.0)
        assertEquals("FLYING", a.leg?.mode)
        assertEquals(20 * H, a.leg?.seconds)
        assertTrue(a.title.startsWith("10,"))
        assertEquals("Flew in, 20 h 00", a.detail)
    }

    @Test fun `what else happened that trip is a setlist of its cities with nights and firsts`() {
        val a = ask(kyotoDay, Question.TRIP) as Answer.TripSetlist
        assertEquals(listOf("Kyoto" to 3, "Osaka" to 1), a.cities.map { it.city to it.nights })
        assertTrue(a.cities.all { it.first })
        assertEquals(kyotoTrip.start..kyotoTrip.end, a.range)
        assertEquals(listOf("kyoto-hotel", "kyoto-temple"), a.cities[0].placeIds)
        assertEquals(listOf("osaka-hotel"), a.cities[1].placeIds)
    }

    @Test fun `show me the route covers the trip's legs`() {
        val a = ask(kyotoDay, Question.ROUTE) as Answer.Route
        assertEquals(kyotoTrip.start..kyotoTrip.end, a.range)
        assertEquals(3, a.legs)
        assertEquals(kyotoTrip.start, a.playhead)
    }

    @Test fun `compare years lists where you were on that date each year`() {
        val a = ask(kyotoDay, Question.COMPARE) as Answer.Compare
        assertEquals(listOf(2022 to "Kyoto", 2023 to "Boston"), a.rows.map { it.year to it.city })
        assertEquals(listOf(10_800.0, 3.0), a.rows.map { it.km })
        // "now" marks today's year, and 2025 has no record on that date.
        assertTrue(a.rows.none { it.now })
        val onRecord = data.copy(daily = data.daily + homeDay("2025-11-15"))
        val b = compareYears(onRecord, highlightFor(onRecord, today)!!)!!
        assertEquals(2025, b.rows.single { it.now }.year)
    }

    @Test fun `what did I do that day is a ledger of stops in order`() {
        val a = ask(kyotoDay, Question.DID) as Answer.Ledger
        assertEquals(listOf("Hotel Kanra", "Kiyomizu-dera"), a.stops.map { it.name })
        assertEquals(listOf("lodging", "place_of_worship"), a.stops.map { it.type })
        assertEquals(2.0, a.stops[1].hours, 1e-9)
    }

    @Test fun `have I been back counts later days in that city`() {
        val a = ask(kyotoDay, Question.BACK) as Answer.Back
        assertEquals(1, a.times)
        assertEquals("2024-04-02", a.last)
        assertEquals("Once · last 2 Apr 2024", a.title)
        val never = ask(highlightFor(data, LocalDate.of(2025, 6, 10))!!, Question.BACK) as Answer.Back
        assertEquals(0, never.times)
        assertEquals("Never — a year and counting", never.title)
    }

    @Test fun `was this my farthest ever`() {
        val yes = ask(kyotoDay, Question.FARTHEST) as Answer.Farthest
        assertTrue(yes.isFarthest)
        val no = ask(highlightFor(data, LocalDate.of(2025, 8, 20))!!, Question.FARTHEST) as Answer.Farthest
        assertFalse(no.isFarthest)
        assertEquals("No — Kyoto, 10,800 km, 2022", no.title)
        assertEquals("2022-11-13", no.date)
    }

    @Test fun `this time last year chains to another highlight`() {
        val lisbon = highlightFor(data, LocalDate.of(2025, 8, 20))!!
        val a = ask(lisbon, Question.LAST_YEAR) as Answer.LastYear
        assertEquals("2021-08-20", a.highlight.date)
        assertEquals("Chicago", a.highlight.city)
        // Kyoto has nothing in the Novembers before it, so the question is not offered.
        assertTrue(followUps(data, kyotoDay).none { it.question == Question.LAST_YEAR })
    }

    @Test fun `every answer carries a range and place ids`() {
        for (f in followUps(data, kyotoDay)) {
            assertTrue(f.answer.range.first <= f.answer.range.last, f.question.name)
            assertTrue(f.answer.placeIds.isNotEmpty(), f.question.name)
        }
    }

    @Test fun `answers chain, without repeating the question asked, and stop at depth four`() {
        val stay = followUps(data, kyotoDay).first { it.question == Question.STAY }
        val next = followUps(data, stay, depth = 1).map { it.question }
        assertFalse(Question.STAY in next)
        assertTrue(Question.TRIP in next)
        assertTrue(followUps(data, stay, depth = MAX_DEPTH).isEmpty())
        val lisbon = highlightFor(data, LocalDate.of(2025, 8, 20))!!
        val lastYear = followUps(data, lisbon).first { it.question == Question.LAST_YEAR }
        // A chained highlight card offers its own full menu.
        assertEquals(followUps(data, (lastYear.answer as Answer.LastYear).highlight).map { it.question }, followUps(data, lastYear, depth = 1).map { it.question })
    }

    @Test fun `sunday week variant counts the candidates in the window`() {
        assertEquals(5, weekCandidates(data, LocalDate.of(2025, 11, 15)))
        assertEquals(0, weekCandidates(data, LocalDate.of(2025, 2, 10)))
    }

    // --- cost ---

    @Test fun `a hundred thousand visits index once and answer fast`() {
        val n = 100_000
        val days = 3650
        val vs = ArrayList<Visit>(n); val ds = ArrayList<Daily>(days)
        val start = d("2014-01-01")
        for (i in 0 until days) {
            val day = start + i * D
            val away = i % 90 < 5
            val lat = if (away) kyoto.lat else boston.lat; val lng = if (away) kyoto.lng else boston.lng
            val pid = if (away) "kyoto-hotel" else "home"
            vs.add(visit(day + 18 * H, day + 32 * H, pid, lat = lat, lng = lng, id = vs.size))
            val perDay = n / days - 1
            for (k in 0 until perDay) {
                val s = day + 8 * H + k * (10 * H / perDay)
                vs.add(visit(s, s + 20 * 60, if (away) "kyoto-temple" else "work", lat = lat + 0.01, lng = lng, id = vs.size))
            }
            ds.add(if (away) daily(LocalDate.ofEpochDay(day / D).toString(), nPlaces = perDay + 1, cities = listOf("Kyoto"), hoursOther = 20.0, maxDistFromHomeKm = 10_800.0) else homeDay(LocalDate.ofEpochDay(day / D).toString()))
        }
        vs.sortBy { it.start }
        val big = Fx.record(vs, emptyList(), places, ds, emptyList(), home = boston, range = start..(start + days * D - 1))
        val before = indexBuilds
        val t0 = System.nanoTime()
        val h = highlightFor(big, LocalDate.of(2024, 1, 3))
        assertNotNull(h)
        val fs = followUps(big, h!!)
        assertTrue(fs.isNotEmpty())
        for (q in listOf(LocalDate.of(2024, 5, 3), LocalDate.of(2024, 9, 3))) highlightFor(big, q)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(before + 1, indexBuilds, "the index is built once per record")
        assertTrue(ms < 3000, "took $ms ms")
    }
}
