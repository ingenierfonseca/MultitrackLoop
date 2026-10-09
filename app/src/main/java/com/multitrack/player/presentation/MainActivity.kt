package com.multitrack.player.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.multitrack.player.presentation.navigation.MultitrackNavHost
import com.multitrack.player.presentation.theme.MultitrackPlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultitrackPlayerTheme {
                MultitrackNavHost()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // The PlaybackViewModel releases the engine on onCleared; nothing else to do.
    }
}
