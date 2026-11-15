package com.elsewhere.app.share

import kotlin.math.min
import kotlin.math.roundToInt

// Port of the sheet maths in web/src/share/saveCard.ts. Everything here is in output pixels.

/** As-is on its own stock, or letterboxed for a 9:16 story or a 4:5 post. */
enum class ShareFormat(val suffix: String, val sheetWidth: Int, val sheetHeight: Int?, val label: String) {
    FREE("", 1080, null, "Free"),
    STORY("-story", 1080, 1920, "Story 9:16"),
    POST("-post", 1080, 1350, "Post 4:5"),
}

/** Every card is laid out at this width in dp; the density is then chosen so it fills its box on the sheet. */
const val CARD_DP = 400f
private const val FREE_PAD = 72
private const val SHEET_PAD = 60
/** The brand line under a story or post. */
const val FOOT_PX = 120

/** `elsewhere-<slug>[-story|-post].png`, the slug cut to 48 characters. */
fun cardFileName(name: String, format: ShareFormat): String {
    val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(48).ifEmpty { "card" }
    return "elsewhere-$slug${format.suffix}.png"
}

/** Where the card lands: its density (px per dp) and its box on a sheet of `sheetWidth` × `sheetHeight`. */
data class CardPlacement(val density: Float, val x: Int, val y: Int, val cardWidth: Int, val cardHeight: Int, val sheetWidth: Int, val sheetHeight: Int)

fun cardPlacement(format: ShareFormat, cardHeightDp: Float): CardPlacement {
    val w = format.sheetWidth
    val h = format.sheetHeight
    if (h == null) {
        val d = (w - FREE_PAD * 2) / CARD_DP
        val ch = (cardHeightDp * d).roundToInt()
        return CardPlacement(d, FREE_PAD, FREE_PAD, w - FREE_PAD * 2, ch, w, ch + FREE_PAD * 2)
    }
    val room = h - SHEET_PAD * 2 - FOOT_PX
    val d = min((w - SHEET_PAD * 2) / CARD_DP, room / cardHeightDp)
    val cw = (CARD_DP * d).toInt()
    val ch = (cardHeightDp * d).roundToInt()
    return CardPlacement(d, (w - cw) / 2, (h - FOOT_PX - ch) / 2, cw, ch, w, h)
}
