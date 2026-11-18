package com.elsewhere.app.record

import com.elsewhere.app.data.Recording

/** "last fix 2 min ago", for the notification and the status bill. */
fun lastFixText(lastFixTs: Long?, now: Long): String {
    if (lastFixTs == null) return "no fix yet"
    val s = (now - lastFixTs).coerceAtLeast(0)
    val rel = when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} days ago"
    }
    return "last fix $rel"
}

/**
 * Whether a start intent means "sit idle". An explicit pause says so. Any start without an action,
 * a plain `start()` or a redelivery after the system killed us, must ask the settings: they are the
 * truth, and guessing here once paused every recorder on every start.
 */
fun pausedFor(action: String?, recording: () -> Recording, now: Long): Boolean =
    if (action == null) !recording().active(now) else action == RecordService.ACTION_PAUSE

fun notificationText(lastFixTs: Long?, now: Long) = "Elsewhere is recording · ${lastFixText(lastFixTs, now)}"
