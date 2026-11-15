package com.elsewhere.app.ingest

import com.elsewhere.app.data.MapsSettings
import com.elsewhere.app.data.Settings
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Is the visitor allowed past the key? Not while a key is stored but unproven on both APIs and limited maps are not yet accepted (web `useKeyGate`). */
fun keyGated(s: Settings): Boolean = s.mapsKey.isNotBlank() && !(s.keyPlaces && s.keyTiles) && !s.limitedAccepted

/** Saves a key and records Google's verdict on it; the onboarding door and the startup gate both go through here. */
@Singleton
class KeyVerifier @Inject constructor(private val maps: MapsSettings, private val checker: KeyChecker) {
    /** Save the key, run both probes, store what they said. A new key is unproven until its probes answer: nothing paid may run on it in the meantime. */
    suspend fun verify(key: String, mapId: String): KeyReport {
        val cur = maps.settings.first()
        val fresh = if (key == cur.mapsKey) cur else cur.copy(keyPlaces = false, keyTiles = false, keyCheckedAt = 0, limitedAccepted = false)
        maps.saveSettings(fresh.copy(mapsKey = key, mapId = mapId))
        val report = checker.run(key)
        val now = maps.settings.first()
        maps.saveSettings(now.copy(keyPlaces = report.places == Probe.Ok, keyTiles = report.tiles == Probe.Ok, keyCheckedAt = System.currentTimeMillis()))
        return report
    }

    /** The user ticked "I understand" on limited maps: remember it. */
    suspend fun acceptLimited() { maps.saveSettings(maps.settings.first().copy(limitedAccepted = true)) }
}
