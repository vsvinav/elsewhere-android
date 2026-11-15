package com.elsewhere.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.elsewhere.app.ui.Root
import com.elsewhere.app.ui.world.ElsewhereTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The sheet is always light, so bar icons must stay dark whatever the system theme says.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { ElsewhereTheme { Root() } }
    }
}
