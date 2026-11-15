package com.elsewhere.app.data

import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

/** The writing side of the record, as the front door needs it; tests stand in with a fake. */
interface RecordStore {
    val hasImport: StateFlow<Boolean>
    val profile: StateFlow<Profile?>
    suspend fun import(open: () -> InputStream, fileName: String, onProgress: (Float) -> Unit = {}): ImportReport
    /** The record derived before any home is known, for ranking home and work candidates. */
    suspend fun provisionalRecord(): Record?
    suspend fun saveProfile(p: Profile)
}

/** Thrown by `import` when the file parses but holds no Timeline segments. */
class NotATimelineExport : RuntimeException("No visits or activities found. Is this a Google Timeline export (semanticSegments)?")
