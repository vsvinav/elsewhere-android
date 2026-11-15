package com.elsewhere.app.ui.onboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.InkButton
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.PosterType
import com.elsewhere.app.ui.world.PosterTextField

/** The optional Google key: maps and names with it, OpenStreetMap without. */
@Composable
fun KeyScreen(key: String, mapId: String, onSave: (String, String) -> Unit, onSkip: () -> Unit, onCancel: (() -> Unit)?) {
    var k by rememberSaveable(key) { mutableStateOf(key) }
    var id by rememberSaveable(mapId) { mutableStateOf(mapId) }
    OnboardFrame(question = null, onCancel = onCancel) {
        DisplayText("A Maps key", 64.sp)
        Spacer(Modifier.height(28.dp))
        Text("A Google Maps key draws the maps and names your stops. Skip and we'll use OpenStreetMap.", color = Poster.Ink2, style = PosterType.Lede.copy(fontSize = 18.sp))
        Spacer(Modifier.height(32.dp))
        PosterTextField(k, { k = it }, Modifier.fillMaxWidth(), placeholder = "AIza…", label = "Google Maps key", mono = true)
        Spacer(Modifier.height(24.dp))
        PosterTextField(id, { id = it }, Modifier.fillMaxWidth(), placeholder = "none", label = "Map ID · optional, vector tiles", mono = true)
        Spacer(Modifier.height(16.dp))
        Text("The key stays on this phone. Enable Maps SDK for Android and Places API (New) on it.", color = Poster.Ink3, style = PosterType.Label)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InkButton("Use this key", { onSave(k, id) }, enabled = k.isNotBlank())
            InkButton("Skip", onSkip, quiet = true)
        }
    }
}
