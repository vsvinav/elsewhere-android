package com.elsewhere.app.ui.crew

import com.elsewhere.app.crew.Crypto
import com.elsewhere.app.crew.Friend
import com.elsewhere.app.crew.FriendFix
import com.elsewhere.app.crew.Grade
import com.elsewhere.app.crew.Grants
import com.elsewhere.app.crew.ShareCode
import com.elsewhere.app.crew.ShareLength
import com.elsewhere.app.crew.ShareSession
import com.elsewhere.app.data.Recording

/** A crew of four and a share in progress, for snapshots and screen tests. */
object CrewFixtures {
    const val NOW = 1_763_222_520_000L

    private fun key(seed: Int) = Crypto.hex(ByteArray(32) { seed.toByte() })
    fun friend(id: String, name: String, grants: Grants, addedAt: Long = NOW - 12 * 86_400_000L) =
        Friend(id, name, key(id.hashCode() and 0x7f), key(1), "PAIR$id", Crypto.b64(ByteArray(32) { 2 }), addedAt, grants)

    val marta = friend("M", "Marta", Grants(liveCity = true, sonar = true))
    val kenji = friend("K", "Kenji", Grants())
    val asta = friend("A", "Ásta", Grants(livePrecise = true, liveCity = true))
    val jules = friend("J", "Jules", Grants(sonar = true))
    val friends = listOf(marta, kenji, asta, jules)

    fun fix(f: Friend, lat: Double, lng: Double, city: String?, ageS: Long, grade: Grade = Grade.city) =
        FriendFix(f.deviceId, f.name, lat, lng, city, grade, NOW / 1000 - ageS, NOW - ageS * 1000)

    val fixes = mapOf(
        marta.deviceId to fix(marta, 38.72, -9.14, "Lisbon", 180),
        asta.deviceId to fix(asta, 64.13, -21.9, "Reykjavík", 540, Grade.precise),
        kenji.deviceId to fix(kenji, 35.01, 135.77, "Kyoto", 3 * 3600),
    )

    val code = ShareCode("PT-LIS-7Q4K", NOW - 3_600_000L)
    val share = ShareSession(setOf(marta.deviceId, asta.deviceId), NOW - 42 * 60_000L, ShareLength.HOUR.until(NOW - 42 * 60_000L))

    fun ui(
        friends: List<Friend> = this.friends, share: ShareSession? = null, pair: PairState = PairState.Idle, relay: Boolean = true,
        recording: Boolean = true, fixes: Map<String, FriendFix> = this.fixes, code: ShareCode? = this.code,
    ) = CrewUi(friends, code, pair, share, relay, Recording(enabled = recording, since = NOW / 1000 - 3600), fixes, NOW)

    object NoOp : CrewActions {
        override fun newCode() {}
        override fun claim(raw: String) {}
        override fun confirmPair() {}
        override fun dismissPair() {}
        override fun setGrants(deviceId: String, grants: Grants) {}
        override fun remove(deviceId: String) {}
        override fun muteToday(deviceId: String) {}
        override fun unmute(deviceId: String) {}
        override fun startShare(friendIds: Set<String>, length: ShareLength) {}
        override fun stopShare() {}
        override fun startRecording() {}
    }
}
