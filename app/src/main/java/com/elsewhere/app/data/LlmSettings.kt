package com.elsewhere.app.data

import com.elsewhere.app.ask.LlmConfig
import kotlinx.coroutines.flow.Flow

/** The "Ask Elsewhere" provider, key, model and base URL, read and written; `SettingsStore` is the real one. */
interface LlmSettings {
    val llm: Flow<LlmConfig>
    suspend fun saveLlm(c: LlmConfig)
}
