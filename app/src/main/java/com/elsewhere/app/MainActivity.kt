package com.elsewhere.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.elsewhere.app.ingest.CostMeter
import com.elsewhere.app.ingest.Kind
import com.elsewhere.app.ingest.Wikimedia
import com.elsewhere.app.ui.world.LocalPhotoSources
import com.elsewhere.app.ui.world.PhotoSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.elsewhere.app.ui.Root
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.highlights.PendingHighlight
import com.elsewhere.app.crew.RoomHub
import com.elsewhere.app.sonar.PendingCrew
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    /** The notification's PendingIntent is SINGLE_TOP, so a tap while the app is alive lands here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PendingHighlight.take(intent)
        PendingCrew.take(intent)
    }

    @Inject lateinit var meter: CostMeter
    @Inject lateinit var hub: RoomHub

    override fun onStart() { super.onStart(); hub.foreground(true) }
    override fun onStop() { hub.foreground(false); super.onStop() }
    @Inject lateinit var wikimedia: Wikimedia
    @Inject lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The sheet is always light, so bar icons must stay dark whatever the system theme says.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val photos = PhotoSources(
            onGooglePhoto = { appScope.launch { meter.tally(Kind.PHOTOS) } },
            commons = { lat, lng -> wikimedia.photo(lat, lng) },
        )
        // A tap on the daily highlight lands here with the day; the Facts tab prints that card first.
        PendingHighlight.take(intent)
        PendingCrew.take(intent)
        setContent { CompositionLocalProvider(LocalPhotoSources provides photos) { ElsewhereTheme { Root() } } }
    }
}
