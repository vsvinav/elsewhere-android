package com.elsewhere.app.ui.tabs.highlights

import com.elsewhere.app.analytics.Highlight
import com.elsewhere.app.analytics.Scope

// Twin of web/src/tabs/highlights/copy.ts.

/** Anything a door opens: the exact range, where the playhead should rest, and the places involved. */
typealias OpenDoor = (range: LongRange, playhead: Long?, placeIds: List<String>) -> Unit

/** "2 years ago" / "last year" / "earlier this year". */
fun ago(yearsAgo: Int): String = when { yearsAgo <= 0 -> "earlier this year"; yearsAgo == 1 -> "last year"; else -> "$yearsAgo years ago" }

/** "On this day, 2 years ago — you woke up in Kyoto." */
fun headline(h: Highlight): String = "${if (h.scope == Scope.WEEK) "This week" else "On this day"}, ${ago(h.yearsAgo)} — you ${h.verb.text} ${h.city}."

private val MONTH = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
/** "15 November" from YYYY-MM-DD, for copy that names the calendar day. */
fun dayMonth(date: String): String { val (_, m, d) = date.split("-").map { it.toInt() }; return "$d ${MONTH[m - 1]}" }
