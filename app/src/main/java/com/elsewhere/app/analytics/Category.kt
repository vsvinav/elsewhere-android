package com.elsewhere.app.analytics

import com.elsewhere.app.data.Place
import com.elsewhere.app.data.Role

// Port of web/src/analytics/category.ts

/** Kinds of stop, read off what Google says a place is. Places never looked up land in `OTHER`. */
enum class Cat(val key: String, val label: String, val color: String) {
    EAT("eat", "Food & drink", "#d63c20"),
    STAY("stay", "Stays", "#e9b531"),
    SHOP("shop", "Shops", "#e8762c"),
    CULTURE("culture", "Out & about", "#1e4f8f"),
    TRANSIT("transit", "Transit", "#3f8f7a"),
    WORK("work", "Work & study", "#141414"),
    OTHER("other", "Unsorted", "#6b4e3d"),
}

val CATS: List<Cat> = Cat.entries

private val RULES: List<Pair<Cat, Regex>> = listOf(
    Cat.TRANSIT to Regex("station|airport|_stop$|terminal|transit|port$"),
    Cat.STAY to Regex("hotel|lodging|motel|resort|hostel|inn$|guest_house|campground"),
    Cat.EAT to Regex("restaurant|cafe|coffee|bar$|pub|bakery|food|pastry|dessert|ice_cream|tea_house|brewery|winery|diner"),
    Cat.SHOP to Regex("store|shop|mall|market|grocery|supermarket|pharmacy|boutique|dealer"),
    Cat.CULTURE to Regex("museum|park|plaza|arena|stadium|theat|gallery|tourist|attraction|zoo|aquarium|library|church|temple|mosque|landmark|beach|garden|trail|gym|fitness|spa$|salon|cinema|night_club|amusement"),
    Cat.WORK to Regex("office|business|university|school|college|research|corporate|institute|coworking|government|bank"),
)

fun categoryOf(primaryType: String?, types: List<String>, role: Role?): Cat {
    if (role == Role.WORK) return Cat.WORK
    val hay = (listOf(primaryType ?: "") + types).joinToString(" ")
    if (hay.isBlank()) return Cat.OTHER
    for ((cat, re) in RULES) if (re.containsMatchIn(hay)) return cat
    return Cat.OTHER
}

fun categoryOf(p: Place): Cat = categoryOf(p.primaryType, p.types, p.role)
