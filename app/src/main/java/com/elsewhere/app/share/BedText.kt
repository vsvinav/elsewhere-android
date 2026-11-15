package com.elsewhere.app.share

import com.elsewhere.app.analytics.Night
import com.elsewhere.app.analytics.NightKind
import java.time.LocalDate

// Port of web/src/share/text.ts; `shareText` lives in ShareSheet.kt.

const val SITE = "github.com/vsvinav/elsewhere"
private val CELL: Map<NightKind, String> = mapOf(NightKind.HOME to "⬛", NightKind.NEW to "🟥", NightKind.REPEAT to "🟨", NightKind.NONE to "⬜")

/**
 * The bed strip as text people can paste anywhere: one row per month (31 cells, right-padded)
 * for anything longer than ten weeks, else one row per week, Monday first.
 */
fun bedText(nights: List<Night>, title: String): String {
    if (nights.isEmpty()) return "$title\n$SITE"
    val away = nights.filter { it.kind == NightKind.NEW || it.kind == NightKind.REPEAT }
    val cities = away.mapNotNull { it.city?.takeIf { c -> c.isNotEmpty() } }.toSet()
    val caption = "$title · ${away.size} ${if (away.size == 1) "night" else "nights"} away" +
        if (cities.isNotEmpty()) " · ${cities.size} ${if (cities.size == 1) "city" else "cities"}" else ""
    val rows = ArrayList<String>()
    if (nights.size > 70) {
        val byMonth = LinkedHashMap<String, MutableList<Night>>()
        for (n in nights) byMonth.getOrPut(n.date.substring(0, 7)) { ArrayList() }.add(n)
        for (ns in byMonth.values) rows.add(ns.joinToString("") { CELL.getValue(it.kind) })
    } else {
        val pad = LocalDate.parse(nights[0].date).dayOfWeek.value - 1
        val cells = List(pad) { "　" } + nights.map { CELL.getValue(it.kind) }
        for (i in cells.indices step 7) rows.add(cells.subList(i, minOf(i + 7, cells.size)).joinToString(""))
    }
    return (listOf(caption) + rows + SITE).joinToString("\n")
}
