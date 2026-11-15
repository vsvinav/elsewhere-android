package com.elsewhere.app

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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    @Inject lateinit var meter: CostMeter
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
        setContent { CompositionLocalProvider(LocalPhotoSources provides photos) { ElsewhereTheme { Root() } } }
    }
}
