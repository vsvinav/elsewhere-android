package com.elsewhere.app.ui.nav

import com.elsewhere.app.ui.Tab
import com.elsewhere.app.ui.shell.DAY
import com.elsewhere.app.ui.shell.RangeStore
import com.elsewhere.app.ui.shell.midnight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * What the Map tab applies when a door opens: the pin to select, the stops to frame (null frames every shown
 * pin), and the window the pins must have been cut to, so a stale pin set is never framed. A door that names
 * pins also shows every kind of stop again, or its pins might be hidden. `n` counts doors, so the same door
 * twice still re-frames.
 */
data class Opening(val select: String?, val fit: List<String>?, val range: LongRange, val clearHidden: Boolean = fit != null, val n: Int = 0)

private fun onMap(t: Tab) = t == Tab.MAP || t == Tab.REPLAY

/**
 * Opens doors. A door moves the shared window, tells the map what to select and frame, asks the shell to
 * show the Map tab, and remembers where the reader was so the map's tag can take them back. The origin
 * lasts until they go back, leave the map by the strip, or move the window or the pin by hand.
 */
@Singleton
class DoorNav @Inject constructor(private val store: RangeStore, scope: CoroutineScope) {
    private val _origin = MutableStateFlow<Origin?>(null)
    val origin: StateFlow<Origin?> = _origin
    private val _opening = MutableStateFlow<Opening?>(null)
    val opening: StateFlow<Opening?> = _opening
    private val _jump = MutableStateFlow<Tab?>(null)
    /** The tab the shell should show next; cleared with [jumped]. */
    val jump: StateFlow<Tab?> = _jump
    /** The window and pin as the door left them; any other window or pin is the reader's own doing. */
    private var applied: LongRange? = null
    /** The pin as the map last reported it; only a different, real pin counts as leaving the door. */
    private var pin: String? = null
    private var doors = 0

    init { store.range.onEach { r -> if (_origin.value != null && r != applied) dismiss() }.launchIn(scope) }

    fun go(door: Door, from: Tab) {
        val rec = store.raw.value ?: return
        val bounds = rec.meta.range
        val cur = store.range.value
        fun clamp(a: Long, b: Long) = max(bounds.first, a)..min(bounds.last, b)
        val range = when (door) {
            is Door.Range -> clamp(door.from, door.to)
            is Door.Trip -> clamp(door.from, door.to)
            // The moment the figure names must be inside the window, or its pin would not be on the map.
            is Door.Place -> door.at?.takeIf { it !in cur }?.let { clamp(min(cur.first, midnight(it)), max(cur.last, midnight(it) + DAY - 1)) } ?: cur
            is Door.Places -> cur
        }
        val n = ++doors
        val opening = when (door) {
            is Door.Place -> Opening(door.placeId, listOf(door.placeId), range, n = n)
            is Door.Places -> Opening(null, door.placeIds, range, n = n)
            else -> Opening(null, null, range, n = n)
        }
        // The window watcher must know this move is ours before it sees it.
        applied = range
        pin = opening.select
        // A door opened from the map itself keeps the first way back; one with no way back returns to the Tour.
        val prior = _origin.value
        _origin.value = if (onMap(from) && prior != null) prior.copy(label = door.label) else Origin(door.label, if (onMap(from)) Tab.TOUR else from)
        _opening.value = opening
        store.range.value = range
        _jump.value = Tab.MAP
    }

    fun jumped() { _jump.value = null }
    fun opened() { _opening.value = null }

    /** The pin the map now shows. Picking a different pin closes the way back; closing the card or re-picking the same one does not. */
    fun pinChanged(id: String?) { if (_origin.value != null && id != null && id != pin) dismiss(); pin = id }

    /** Back through the door: the shell is asked for the tab the reader came from, and the origin is forgotten. */
    fun back() { val o = _origin.value ?: return; dismiss(); _jump.value = o.backTab }

    /** The reader left the map some other way; the tag would be stale when they return. */
    fun leave() = dismiss()

    private fun dismiss() { _origin.value = null; _opening.value = null; applied = null }
}
