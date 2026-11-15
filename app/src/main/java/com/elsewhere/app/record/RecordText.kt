package com.elsewhere.app.record

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

fun notificationText(lastFixTs: Long?, now: Long) = "Elsewhere is recording · ${lastFixText(lastFixTs, now)}"
