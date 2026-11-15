package com.elsewhere.app.crew

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageTest {
    private val vectorPlain = """{"type":"ping","t":1763222520,"grade":"sonar","lat":38.7223,"lng":-9.1393,"city":"Lisbon","habitat":false,"absenceDays":212,"hit":null}"""

    @Test fun `a ping encodes in the agreed field order, with an explicit null hit`() {
        val p = Ping(t = 1763222520, grade = Grade.sonar, lat = 38.7223, lng = -9.1393, city = "Lisbon", habitat = false, absenceDays = 212)
        assertEquals(vectorPlain, p.encode())
        assertEquals(p, Ping.decode(vectorPlain))
    }

    @Test fun `a hit rides along`() {
        val p = Ping(t = 1, grade = Grade.precise, lat = 0.0, lng = 0.0, hit = Ping.Hit("PAIR:1", 1))
        assertEquals(""""hit":{"id":"PAIR:1","t":1}}""", p.encode().substringAfter("\"absenceDays\":null,"))
    }

    @Test fun `messages are typed by their type field`() {
        assertEquals(Message.Stopped("A"), Message.parse("A", STOP_JSON))
        assertEquals(Message.Revoked("A"), Message.parse("A", REVOKE_JSON))
        assertEquals(Ping.decode(vectorPlain), (Message.parse("A", vectorPlain) as Message.Pinged).ping)
        assertNull(Message.parse("A", """{"type":"dance"}"""))
        assertNull(Message.parse("A", "not json"))
    }
}
