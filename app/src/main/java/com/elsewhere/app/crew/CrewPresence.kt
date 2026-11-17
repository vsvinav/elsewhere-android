package com.elsewhere.app.crew

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Whether any friend's last fix is still fresh, for the dot on the Crew stub. */
interface CrewPresence {
    val live: Flow<Boolean>

    object None : CrewPresence { override val live: Flow<Boolean> = flowOf(false) }
}
