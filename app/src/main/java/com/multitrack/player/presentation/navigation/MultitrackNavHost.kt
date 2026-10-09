package com.multitrack.player.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.multitrack.player.presentation.screens.playback.PlaybackScreen
import com.multitrack.player.presentation.screens.trackselection.TrackSelectionScreen

@Composable
fun MultitrackNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = NavRoutes.TRACK_SELECTION
    ) {
        composable(NavRoutes.TRACK_SELECTION) {
            TrackSelectionScreen(
                onNavigateToPlayback = { session ->
                    val encoded = SessionCodec.encode(session)
                    navController.navigate(NavRoutes.playback(encoded))
                }
            )
        }
        composable(
            route = NavRoutes.PLAYBACK,
            arguments = listOf(navArgument("session") { type = NavType.StringType })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("session").orEmpty()
            val session = SessionCodec.decode(raw)
            PlaybackScreen(session = session)
        }
    }
}
