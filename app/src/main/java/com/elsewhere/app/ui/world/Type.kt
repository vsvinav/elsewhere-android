package com.elsewhere.app.ui.world

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.elsewhere.app.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

val DisplayFamily = variable(R.font.big_shoulders_display_var, 500, 700, 900)
val BodyFamily = variable(R.font.archivo_var, 400, 600, 700)

private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

object PosterType {
    /** Headline numbers and words. Pass text already upper-cased. */
    val Display = TextStyle(
        fontFamily = DisplayFamily, fontWeight = FontWeight.Black, lineHeight = 0.82.em, letterSpacing = (-0.005).em,
        fontFeatureSettings = "tnum", lineHeightStyle = Tight, platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val Caps = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.22.em, lineHeight = 1.em)
    val Lede = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 1.4.em)
    val Label = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.04.em)

    fun material() = Typography(
        displayLarge = Display.copy(fontSize = 64.sp), displayMedium = Display.copy(fontSize = 48.sp), displaySmall = Display.copy(fontSize = 36.sp),
        headlineLarge = Display.copy(fontSize = 32.sp), headlineMedium = Display.copy(fontSize = 28.sp), headlineSmall = Display.copy(fontSize = 24.sp),
        bodyLarge = Lede, bodyMedium = Lede.copy(fontSize = 15.sp), bodySmall = Label,
        labelLarge = Caps, labelMedium = Label, labelSmall = Label.copy(fontSize = 11.sp),
    )
}

/**
 * Display type, upper-cased. With `misreg` the red plate prints 2dp off register beneath the ink,
 * the way the web shadows its headlines.
 */
@Composable
fun DisplayText(
    text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = Poster.Ink, misreg: Boolean = false,
    textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE,
) {
    val style = PosterType.Display.copy(fontSize = size)
    val caps = text.uppercase()
    Box(modifier) {
        if (misreg) Text(caps, Modifier.offset(2.dp, 2.dp), color = Poster.Red, style = style, textAlign = textAlign, maxLines = maxLines)
        Text(caps, color = color, style = style, textAlign = textAlign, maxLines = maxLines)
    }
}

@Composable
fun CapsText(text: String, modifier: Modifier = Modifier, color: Color = Poster.Ink3, size: TextUnit = 13.sp) {
    Text(text.uppercase(), modifier, color = color, style = PosterType.Caps.copy(fontSize = size))
}
