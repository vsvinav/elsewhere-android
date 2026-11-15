package com.elsewhere.app.ui

/** The eight stubs on the ticket strip, in print order. */
enum class Tab(val route: String, val label: String) {
    TOUR("tour", "Tour"), NUMBERS("numbers", "Numbers"), FACTS("facts", "Facts"), STOPS("stops", "Stops"),
    DATES("dates", "Dates"), FLIGHTS("flights", "Flights"), MAP("map", "Map"), REPLAY("replay", "Replay");

    companion object {
        fun byRoute(route: String?): Tab = entries.firstOrNull { it.route == route } ?: TOUR
    }
}
