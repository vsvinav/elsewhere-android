package com.elsewhere.app.data

import kotlinx.coroutines.flow.StateFlow

/** The derived record as a stream; the repository is the real one, tests hand over a MutableStateFlow. */
interface RecordSource {
    val record: StateFlow<Record?>
    /** Build the record again from what is stored now, the export and whatever the phone has recorded since, and return once it has. */
    suspend fun refresh()
}
