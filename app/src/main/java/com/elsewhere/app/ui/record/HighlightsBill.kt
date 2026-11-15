package com.elsewhere.app.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.highlights.DEFAULT_MINUTE
import com.elsewhere.app.ui.world.Bill
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.Field
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Lede
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.TickRow
import java.util.Locale

/** "HH:MM" for a minute of the day. */
fun hhmm(minute: Int) = String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)

/** The daily "On this day" notification: off by default, on at a chosen hour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsBill(at: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Bill(modifier = modifier) {
        DisplayText("On this day", 48.sp, misreg = true)
        Spacer(Modifier.height(16.dp))
        Lede("Once a day, a quiet note about where you were on this date in another year. Nothing on days the record has nothing to say.")
        Spacer(Modifier.height(16.dp))
        TickRow(at != null, { on -> onChange(if (on) DEFAULT_MINUTE else null) }, "Send a daily highlight")
        if (at != null) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.clickable(role = Role.Button) { picking = true }.semantics { contentDescription = "Change the hour" }) {
                Field("At", hhmm(at))
            }
        }
    }
    if (picking && at != null) {
        val state = rememberTimePickerState(initialHour = at / 60, initialMinute = at % 60, is24Hour = true)
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Poster.Stock, contentColor = Poster.Ink, shape = RectangleShape, dragHandle = null) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp)) {
                CapsText("Highlight at")
                Spacer(Modifier.height(12.dp))
                TimePicker(state)
                Spacer(Modifier.height(12.dp))
                InkButton("Set", { onChange(state.hour * 60 + state.minute); picking = false }, Modifier.fillMaxWidth())
            }
        }
    }
}
