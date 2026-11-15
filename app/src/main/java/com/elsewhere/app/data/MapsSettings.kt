package com.elsewhere.app.data

import kotlinx.coroutines.flow.Flow

/** The Google Maps key and map id, read and written; `SettingsStore` is the real one. */
interface MapsSettings {
    val settings: Flow<Settings>
    suspend fun saveSettings(s: Settings)
}
