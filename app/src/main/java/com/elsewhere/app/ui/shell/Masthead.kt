package com.elsewhere.app.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.data.Counters
import com.elsewhere.app.ingest.dollars
import com.elsewhere.app.data.Units
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import java.util.Locale

/** The web's `.tourline`: the mark, "ELSEWHERE · {name}'s record", and the ▾ account menu. */
@Composable
fun Masthead(
    name: String?, units: Units, counters: Counters,
    onHome: () -> Unit, onChangeAnswers: () -> Unit, onMapsKey: () -> Unit, onToggleUnits: () -> Unit, onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().background(Poster.Stock).windowInsetsPadding(WindowInsets.statusBars).height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).clickable(onClick = onHome), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Poster.Red), contentAlignment = Alignment.Center) { DisplayText("E", 20.sp, color = Poster.Stock) }
            Spacer(Modifier.width(14.dp))
            Text(
                buildAnnotatedString {
                    append("ELSE"); withStyle(SpanStyle(color = Poster.Red)) { append("WHERE") }
                    append(" · "); append(if (!name.isNullOrBlank()) "${name.uppercase(Locale.US)}'S RECORD" else "YOUR RECORD")
                },
                color = Poster.Ink, style = PosterType.Caps.copy(fontSize = 12.sp), maxLines = 1,
            )
        }
        Box {
            Text(
                "▾",
                Modifier.clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "Menu" }.padding(horizontal = 10.dp, vertical = 12.dp),
                color = Poster.Ink, style = PosterType.Label,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Poster.Stock, border = BorderStroke(2.dp, Poster.Ink), shape = RectangleShape) {
                MenuRow("Change answers") { menu = false; onChangeAnswers() }
                MenuRow("Maps key") { menu = false; onMapsKey() }
                MenuRow("Units: ${units.name}") { onToggleUnits() }
                MenuRow("Usage: ${counters.detailsCalls} names · ${counters.photoCalls} photos · $${"%.2f".format(Locale.US, counters.dollars())}", color = Poster.Ink3, enabled = false) {}
                MenuRow("Start over", color = Poster.Red) { menu = false; confirm = true }
            }
        }
    }
    if (confirm) ConfirmStartOver(onDismiss = { confirm = false }, onConfirm = { confirm = false; onStartOver() })
}

@Composable
private fun MenuRow(text: String, color: Color = Poster.Ink, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text.uppercase(), color = color, style = PosterType.Caps.copy(fontSize = 11.sp)) },
        onClick = onClick, enabled = enabled,
    )
}
