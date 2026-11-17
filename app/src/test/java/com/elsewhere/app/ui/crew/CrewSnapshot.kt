package com.elsewhere.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.SampleRecord
import com.elsewhere.app.crew.PairRecord
import com.elsewhere.app.crew.Pending
import com.elsewhere.app.map.PinLayers
import com.elsewhere.app.map.PinOpts
import com.elsewhere.app.record.NowStamp
import com.elsewhere.app.sonar.SonarHit
import com.elsewhere.app.ui.tabs.map.MapActions
import com.elsewhere.app.ui.tabs.map.MapScreen
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

/** Mocks 12 and 13: the Crew page, the share sheet, and the Map wearing its Friends layer with a Sonar hit. */
class CrewSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 3000), theme = "android:Theme.Material.Light.NoActionBar")

    private fun shoot(content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        ElsewhereTheme { CompositionLocalProvider(LocalMotion provides false, content = content) }
    }

    @Test fun crew() = shoot { CrewScreen(CrewFixtures.ui(share = CrewFixtures.share), CrewFixtures.NoOp, onBack = {}, scroll = false, onScan = {}, onShareCode = {}) }

    @Test fun crewRehearsal() = shoot { CrewScreen(CrewFixtures.ui(rehearsal = true), CrewFixtures.NoOp, onBack = {}, scroll = false, onScan = {}, onShareCode = {}) }

    @Test fun sonarSheet() = shoot { SonarSheetBody(SonarHit(CrewFixtures.marta, "h1", CrewFixtures.NOW), NowStamp(38.72, -9.14, "19:04", "Lisbon", "just now"), onDismiss = {}) }

    @Test fun friendDetail() = shoot {
        CrewScreen(CrewFixtures.ui(), CrewFixtures.NoOp, onBack = {}, scroll = false, onScan = {}, onShareCode = {}, openFriend = CrewFixtures.marta.deviceId)
    }

    @Test fun confirm() = shoot {
        ConfirmPairBody("Marta", Pending("PT-LIS-7Q4K", PairRecord(CrewFixtures.marta.signPub, CrewFixtures.marta.kxPub, "Marta")).fingerprint, {}, {})
    }

    @Test fun qr() = shoot { QrSheet(CrewFixtures.code, CrewFixtures.NOW, onClose = {}) }

    @Test fun share() = shoot { ShareSheetBody(CrewFixtures.ui(share = CrewFixtures.share), CrewFixtures.NoOp, onClose = {}) }

    @Test fun shareNeedsRecord() = shoot { ShareSheetBody(CrewFixtures.ui(recording = false), CrewFixtures.NoOp, onClose = {}) }

    @Test fun friendsMap() = shoot {
        val ui = CrewFixtures.ui(share = CrewFixtures.share)
        val friends = FriendsUi(
            ui.fixes.values.map { FriendMark.of(it, ui.now) }.sortedBy { it.name }, layerOn = true, selected = CrewFixtures.marta.deviceId,
            hits = setOf(CrewFixtures.marta.deviceId), myCity = "Lisbon", crew = ui,
        )
        val at = remember { mutableStateOf(mapOf(CrewFixtures.marta.deviceId to Offset(620f, 820f), CrewFixtures.asta.deviceId to Offset(420f, 400f), CrewFixtures.kenji.deviceId to Offset(880f, 980f))) }
        MapScreen(
            PinLayers(SampleRecord.record), PinOpts(), MapActions(), Modifier.height(1200.dp),
            live = NowStamp(38.71, -9.15, "14:02", "Lisbon", "just now"), liveAt = remember { mutableStateOf(Offset(300f, 700f)) },
            friends = friends, friendsAt = at,
        ) { m -> Box(m.fillMaxSize().background(Poster.Stock)) }
    }
}
