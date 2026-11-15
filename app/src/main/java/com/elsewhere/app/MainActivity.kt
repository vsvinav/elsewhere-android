package com.elsewhere.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elsewhere.app.ui.world.CapsText
import com.elsewhere.app.ui.world.DisplayText
import com.elsewhere.app.ui.world.ElsewhereTheme
import com.elsewhere.app.ui.world.Pass
import com.elsewhere.app.ui.world.Poster
import com.elsewhere.app.ui.world.figures.Dial
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            ElsewhereTheme {
                Column(Modifier.fillMaxSize().background(Poster.Stock).safeDrawingPadding().padding(20.dp)) {
                    Pass {
                        CapsText("World tour")
                        Spacer(Modifier.height(12.dp))
                        DisplayText("Elsewhere", 64.sp, misreg = true)
                        Spacer(Modifier.height(24.dp))
                        Dial(SampleHours, 7.5, 22 to 28, "7:30 AM", Modifier.width(220.dp).align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

/** Placeholder until the real screens land. */
private val SampleHours = listOf(1, 0, 0, 0, 0, 2, 6, 14, 22, 12, 8, 9, 11, 7, 6, 8, 12, 17, 13, 8, 5, 3, 2, 1)
