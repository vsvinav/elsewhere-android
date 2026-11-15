package com.elsewhere.app.ui.world.figures

/**
 * Geometry for the clock figure: a conventional 12-hour face with 12 at the top, read clockwise.
 * Angles are degrees in the Compose convention, 0 pointing right and -90 straight up.
 */
enum class DialRing { AM, PM }

class DialTick(val hour: Int, val angle: Float, val big: Boolean)
class DialLabel(val text: String, val angle: Float)
class DialHand(val angle: Float, val meridiem: String)
/** One bar per non-empty hour; `len` is the share of the busiest hour. Mornings sit on the inner ring, afternoons and evenings on the outer. */
class DialBar(val hour: Int, val ring: DialRing, val angle: Float, val len: Float)
/** Clockwise sweep from `from`; a window of 12 h or more becomes a full circle. */
class DialArc(val from: Float, val sweep: Float, val full: Boolean)

class DialGeometry(val ticks: List<DialTick>, val labels: List<DialLabel>, val hand: DialHand, val bars: List<DialBar>, val arc: DialArc?)

private fun mod(x: Double, m: Double) = ((x % m) + m) % m

/** Where an hour (fractional, any day) sits on a 12-hour face. */
fun dialAngle(hour: Double): Float = (mod(hour, 12.0) / 12.0 * 360.0 - 90.0).toFloat()

fun meridiemOf(hour: Double): String = if (mod(hour, 24.0) < 12.0) "AM" else "PM"

fun dialGeometry(hours: List<Int>, hand: Double, arc: Pair<Int, Int>?): DialGeometry {
    val mx = maxOf(1, hours.maxOrNull() ?: 1)
    val ticks = (0 until 12).map { i -> DialTick(if (i == 0) 12 else i, dialAngle(i.toDouble()), i % 3 == 0) }
    val labels = listOf(12, 3, 6, 9).map { DialLabel(it.toString(), dialAngle(it.toDouble())) }
    val bars = hours.withIndex().filter { it.value > 0 }.map { (h, v) ->
        DialBar(h, if (h % 24 < 12) DialRing.AM else DialRing.PM, dialAngle(h + 0.5), v.toFloat() / mx)
    }
    val arcOut = arc?.let { (from, to) ->
        // A window given as 23 -> 7 wraps past midnight; one given as 22 -> 28 already runs forward.
        val span = if (to >= from) to - from else to - from + 24
        val full = span >= 12
        DialArc(dialAngle(from.toDouble()), if (full) 360f else span / 12f * 360f, full)
    }
    return DialGeometry(ticks, labels, DialHand(dialAngle(hand), meridiemOf(hand)), bars, arcOut)
}
