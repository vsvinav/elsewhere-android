package com.elsewhere.app.data

import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

/** The writing side of the record, as the front door needs it; tests stand in with a fake. */
interface RecordStore {
    /** False until both the import state and the profile have been read once; the door can't be chosen before. */
    val ready: StateFlow<Boolean>
    val hasImport: StateFlow<Boolean>
    /** True once there is history from elsewhere: a Timeline export or stays pulled from another device; the phone's own stays do not count. */
    val hasHistory: StateFlow<Boolean>
    /** Distinct days the phone has recorded a stay on. */
    val recordedDays: StateFlow<Int>
    val profile: StateFlow<Profile?>
    suspend fun import(open: () -> InputStream, fileName: String, onProgress: (Float) -> Unit = {}): ImportReport
    /** The record derived before any home is known, for ranking home and work candidates. */
    suspend fun provisionalRecord(): Record?
    suspend fun saveProfile(p: Profile)
    /** When the profile was last changed on this phone, epoch ms; 0 before any. */
    suspend fun profileEditedAt(): Long
    /** Forget the import and the profile; back to the front door. */
    suspend fun reset()
}

/** Thrown by `import` when the file parses but holds no Timeline segments. */
class NotATimelineExport : RuntimeException("No visits or activities found. Is this a Google Timeline export (semanticSegments)?")
