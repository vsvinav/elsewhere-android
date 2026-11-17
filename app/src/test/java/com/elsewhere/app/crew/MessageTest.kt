package com.elsewhere.app.crew

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MessageTest {
    private val vectorPlain = """{"type":"ping","t":1763222520,"grade":"sonar","lat":38.7223,"lng":-9.1393,"city":"Lisbon","habitat":false,"absenceDays":212,"hit":null}"""

    @Test fun `a ping encodes in the agreed field order, with an explicit null hit`() {
        val p = Ping(t = 1763222520, grade = Grade.sonar, lat = 38.7223, lng = -9.1393, city = "Lisbon", habitat = false, absenceDays = 212)
        assertEquals(vectorPlain, p.encode())
        assertEquals(p, Ping.decode(vectorPlain))
    }

    @Test fun `a hit rides along, saying whether it was a rehearsal, and an older hit without the word reads as real`() {
        val p = Ping(t = 1, grade = Grade.precise, lat = 0.0, lng = 0.0, hit = Ping.Hit("PAIR:1", 1))
        assertEquals(""""hit":{"id":"PAIR:1","t":1,"rehearsal":false}}""", p.encode().substringAfter("\"absenceDays\":null,"))
        assertEquals(""""hit":{"id":"PAIR:1","t":1,"rehearsal":true}}""", p.copy(hit = Ping.Hit("PAIR:1", 1, rehearsal = true)).encode().substringAfter("\"absenceDays\":null,"))
        assertEquals(p, Ping.decode("""{"type":"ping","t":1,"grade":"precise","lat":0.0,"lng":0.0,"hit":{"id":"PAIR:1","t":1}}"""))
    }

    @Test fun `messages are typed by their type field`() {
        assertEquals(Message.Stopped("A"), Message.parse("A", STOP_JSON))
        assertEquals(Message.Revoked("A"), Message.parse("A", REVOKE_JSON))
        assertEquals(Ping.decode(vectorPlain), (Message.parse("A", vectorPlain) as Message.Pinged).ping)
        assertNull(Message.parse("A", """{"type":"dance"}"""))
        assertNull(Message.parse("A", "not json"))
    }

    @Test fun `a test ping says so only when true, may have no position, and anything but a bare boolean reads as false`() {
        val p = Ping(t = 1, grade = Grade.sonar, hit = Ping.Hit("ab12cd34", 1), test = true)
        assertEquals("""{"type":"ping","t":1,"grade":"sonar","lat":null,"lng":null,"city":null,"habitat":false,"absenceDays":null,"hit":{"id":"ab12cd34","t":1,"rehearsal":false},"test":true}""", p.encode())
        assertEquals(p, Ping.decode(p.encode()))
        assertFalse(p.placed)
        assertFalse(Ping.decode(vectorPlain).test)
        assertTrue(Ping.decode(vectorPlain.dropLast(1) + ""","test":true}""").test)
        for (odd in listOf("\"true\"", "1", "null", "{}", "[true]")) assertFalse(Ping.decode(vectorPlain.dropLast(1) + ""","test":$odd}""").test, odd)
    }

    @Test fun `a ping whose time is not a number, or missing, is not a ping`() {
        assertNull(Message.parse("A", vectorPlain.replace("\"t\":1763222520", "\"t\":\"1763222520\"")))
        assertNull(Message.parse("A", vectorPlain.replace("\"t\":1763222520,", "")))
        assertNull(Message.parse("A", vectorPlain.replace("\"t\":1763222520", "\"t\":1763222520.5")))
    }
}
