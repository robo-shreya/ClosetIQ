package com.closetiq.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.closetiq.android.ui.navigation.ClosetIqNavHost
import com.closetiq.android.ui.theme.ClosetIQTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The header and tab bar apply their own system-bar insets, so the dark ground
        // runs all the way behind the status and navigation bars.
        enableEdgeToEdge()

        val container = (application as ClosetIqApplication).container

        setContent {
            ClosetIQTheme {
                ClosetIqNavHost(container = container)
            }
        }
    }
}
