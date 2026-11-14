package com.elsewhere.app.ingest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimelineParserTest {
    private val text = javaClass.classLoader.getResource("mini_timeline.json")!!.readText()
    private val parsed = parseTimeline(text)

    @Test fun `parse counts every segment and skips the bad coordinate`() {
        assertEquals(12, parsed.visits.size)
        assertEquals(8, parsed.activities.size)
        assertEquals(1, parsed.paths.size)
        assertEquals(3, parsed.paths[0].pts.size)
        assertEquals(1, parsed.memories.size)
        assertEquals(23, parsed.report.segmentsTotal)
        assertEquals(22, parsed.report.parsed)
        assertEquals(mapOf("bad_latlng" to 1), parsed.report.skippedByReason)
    }

    @Test fun `visit fields`() {
        val v = parsed.visits[0]
        assertEquals("HOME1", v.placeId)
        assertEquals("INFERRED_HOME", v.type)
        assertEquals(42.3632761, v.lat)
        assertEquals(-71.0643721, v.lng)
        assertEquals(-300, v.tz)
        assertEquals(8L * 3600, v.end - v.start)
        assertEquals(0.9, v.p)
        assertEquals(0, v.id)
    }

    @Test fun `hierarchy level`() {
        assertEquals(1, parsed.visits.first { it.placeId == "LUNCH1" }.lvl)
        assertEquals(0, parsed.visits[0].lvl)
    }

    @Test fun `tz prefers the segment offset field over the string offset`() {
        val v = parsed.visits.first { it.placeId == "INDIA1" }
        assertEquals(330, v.tz)
        // 2025-03-08T10:00:00-07:00 == 2025-03-08T17:00:00Z
        assertEquals(1741453200L, v.start)
    }

    @Test fun `tz falls back to the string offset without the field`() {
        assertEquals(-300, parsed.visits[1].tz)
    }

    @Test fun `activity missing probability falls back to the top candidate`() {
        val a = parsed.activities[1]
        assertEquals(0.7, a.p)
        assertEquals("IN_SUBWAY", a.mode)
        assertEquals(5200.0, a.m)
    }

    @Test fun `activity across zones keeps the start-string offset`() {
        val fly = parsed.activities.first { it.mode == "FLYING" }
        assertEquals(-300, fly.tz)
        assertEquals(4340000.0, fly.m)
        assertEquals(0.99, fly.p)
    }

    @Test fun `timeline path points carry their own timestamps`() {
        val p = parsed.paths[0]
        assertEquals(42.3736, p.pts[0].lat)
        assertEquals(-71.1097, p.pts[0].lng)
        // 2025-03-04T17:05:00-05:00 == 22:05Z
        assertEquals(1741125900L, p.pts[0].ts)
        assertTrue(p.start < p.pts[0].ts && p.pts.last().ts <= p.end)
    }

    @Test fun `timeline memory`() {
        val m = parsed.memories[0]
        assertEquals(4340.0, m.km)
        assertEquals(listOf("MTV1"), m.dests)
    }

    @Test fun `frequent places from the user location profile`() {
        assertEquals("HOME", parsed.frequentPlaces[0].label)
        assertEquals(42.3632761, parsed.frequentPlaces[0].lat)
        assertEquals("WORK1", parsed.frequentPlaces[1].placeId)
    }

    @Test fun `records are sorted by start`() {
        assertEquals(parsed.visits.map { it.start }.sorted(), parsed.visits.map { it.start })
        assertEquals(parsed.activities.map { it.start }.sorted(), parsed.activities.map { it.start })
    }

    @Test fun `identical with terminal escape corruption`() {
        val corrupted = text.replaceFirst("\"distanceMeters\"", "\u001b[O\"distanceMeters\"")
        assertEquals(parsed, parseTimeline(corrupted))
    }

    @Test fun `unknown segments and missing fields are reported by reason`() {
        val doc = """{"semanticSegments":[
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z"},
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","visit":{"topCandidate":{"placeLocation":{"latLng":"1°, 2°"}}}},
            {"startTime":"garbage","endTime":"2025-01-01T01:00:00Z","visit":{}},
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","timelinePath":{}}
        ]}"""
        val r = parseTimeline(doc).report
        assertEquals(4, r.segmentsTotal)
        assertEquals(0, r.parsed)
        assertEquals(mapOf("unknown_segment" to 1, "missing_field" to 2, "bad_timestamp" to 1), r.skippedByReason)
    }

    @Test fun `a malformed frequent place is dropped, the rest still parses`() {
        val doc = """{"semanticSegments":[
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","visit":{"topCandidate":{"placeId":"A","placeLocation":{"latLng":"1°, 2°"}}}}
        ],"userLocationProfile":{"frequentPlaces":[{"placeLocation":"1°, 2°","label":"HOME"},{"placeId":"B","placeLocation":"3°, 4°"}]}}"""
        val p = parseTimeline(doc)
        assertEquals(1, p.visits.size)
        assertEquals(listOf("B"), p.frequentPlaces.map { it.placeId })
    }

    @Test fun `timestamps with rolled-over fields are accepted like Date-UTC`() {
        // 2025-02-30 rolls over to 2025-03-02 (non-leap year).
        val doc = """{"semanticSegments":[
            {"startTime":"2025-02-30T00:00:00Z","endTime":"2025-02-28T24:00:00Z","visit":{"topCandidate":{"placeId":"A","placeLocation":{"latLng":"1°, 2°"}}}}
        ]}"""
        val v = parseTimeline(doc).visits.single()
        assertEquals(1740873600L, v.start)
        assertEquals(1740787200L, v.end)
    }

    @Test fun `a null visit goes down the visit branch and reads as missing_field`() {
        val doc = """{"semanticSegments":[
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","visit":null},
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","activity":null},
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","timelinePath":null}
        ]}"""
        assertEquals(mapOf("missing_field" to 3), parseTimeline(doc).report.skippedByReason)
    }

    @Test fun `stream and string entry points agree`() {
        val fromStream = javaClass.classLoader.getResourceAsStream("mini_timeline.json")!!.use { parseTimeline(it) }
        assertEquals(parsed, fromStream)
    }

    @Test fun `semanticSegments after userLocationProfile and other top-level keys still parses`() {
        val doc = """{"rawSignals":[{"x":"]}\"","y":[1,{"z":"}"}]}],
            "userLocationProfile":{"frequentPlaces":[{"placeId":"H","placeLocation":"1°, 2°","label":"HOME"}]},
            "n":12.5,"flag":true,"nothing":null,
            "semanticSegments":[
            {"startTime":"2025-01-01T00:00:00Z","endTime":"2025-01-01T01:00:00Z","visit":{"topCandidate":{"placeId":"A","placeLocation":{"latLng":"1°, 2°"}}}},
            {"startTime":"2025-01-01T01:00:00Z","endTime":"2025-01-01T02:00:00Z","activity":{"start":{"latLng":"1°, 2°"},"end":{"latLng":"1°, 3°"},"distanceMeters":10,"topCandidate":{"type":"WALKING"}}}
        ]}"""
        val p = parseTimeline(doc.byteInputStream())
        assertEquals(listOf("A"), p.visits.map { it.placeId })
        assertEquals(listOf("WALKING"), p.activities.map { it.mode })
        assertEquals(listOf("H"), p.frequentPlaces.map { it.placeId })
        assertEquals(2, p.report.parsed)
    }

    @Test fun `a document without segments parses to nothing`() {
        val p = parseTimeline("{}")
        assertTrue(p.visits.isEmpty() && p.activities.isEmpty() && p.frequentPlaces.isEmpty())
        assertEquals(0, p.report.segmentsTotal)
    }
}
