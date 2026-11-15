package com.elsewhere.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The Google Maps key and map id, read and written; `SettingsStore` is the real one. */
interface MapsSettings {
    val settings: Flow<Settings>
    val counters: Flow<Counters>
    suspend fun saveSettings(s: Settings)
}

private const val REJECT_AFTER = 2

/** One failed session could be the network; two on the same key and the key is taken as refused. */
suspend fun MapsSettings.noteGoogleFailed() {
    val s = settings.first(); val n = s.googleFailures + 1
    saveSettings(s.copy(googleFailures = n, googleRejected = s.googleRejected || n >= REJECT_AFTER))
}

/** A session that loads wipes the slate, so "two failures" means two in a row, not two ever. */
suspend fun MapsSettings.noteGoogleLoaded() {
    val s = settings.first(); if (s.googleFailures != 0) saveSettings(s.copy(googleFailures = 0))
}
