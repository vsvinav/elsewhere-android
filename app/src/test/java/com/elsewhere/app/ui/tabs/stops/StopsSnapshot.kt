package com.elsewhere.app.ui.tabs.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.elsewhere.app.Fx
import com.elsewhere.app.analytics.Cat
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.LocalMotion
import com.elsewhere.app.ui.world.Poster
import org.junit.Rule
import org.junit.Test

class StopsSnapshot {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    /** Two tags side by side: a named shop with its Places street, and an unnamed stop wearing what Nominatim said. */
    @Test fun cardsWithAddress() = paparazzi.snapshot {
        val shop = Fx.place(city = "Boston", cc = "US", name = "Trident Booksellers", nVisits = 14, totalHours = 21.5, lastTs = 1_700_000_000, primaryType = "book_store")
            .copy(address = "338 Newbury St, Boston, MA 02115, USA")
        val stop = Fx.place(city = "Boston", cc = "US", nVisits = 3, totalHours = 4.0, lastTs = 1_690_000_000)
        ElsewhereTheme {
            CompositionLocalProvider(LocalMotion provides false) {
                Row(Modifier.background(Poster.Stock).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Tag(Stop("shop", shop, Cat.SHOP), 0, false, Modifier.weight(1f)) {}
                    Tag(Stop("stop", stop, Cat.OTHER), 1, false, Modifier.weight(1f), nominatim = "2180 Cranston Rd, University Heights") {}
                }
            }
        }
    }
}
