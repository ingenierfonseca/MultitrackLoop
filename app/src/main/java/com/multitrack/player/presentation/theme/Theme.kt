package com.multitrack.player.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppThemeColors(
    val appBackground: Color,
    val panel: Color,
    val card: Color,
    val border: Color,
    val accent: Color,
    val mutedText: Color,
    val mainText: Color,
    val meterGreen: Color,
    val meterYellow: Color,
    val meterRed: Color,
    val meterBackground: Color,
    val trackToggle: Color
)

private val MultitrackColors = AppThemeColors(
    appBackground = MultitrackAppBackground,
    panel = MultitrackPanel,
    card = MultitrackCard,
    border = MultitrackBorder,
    accent = MultitrackCyan,
    mutedText = MultitrackMutedText,
    mainText = MultitrackMainText,
    meterGreen = MultitrackGreen,
    meterYellow = MultitrackYellow,
    meterRed = MultitrackRed,
    meterBackground = MultitrackMeterBackground,
    trackToggle = MultitrackTrackToggle
)

val LocalAppThemeColors = staticCompositionLocalOf { MultitrackColors }

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = CoralSecondary,
    onSecondary = CoralOnSecondary,
    secondaryContainer = CoralSecondaryContainer,
    onSecondaryContainer = CoralOnSecondaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError
)

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = CoralSecondary,
    onSecondary = CoralOnSecondary,
    secondaryContainer = CoralSecondaryContainer,
    onSecondaryContainer = CoralOnSecondaryContainer,
    tertiaryContainer = TertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface
)

@Composable
fun MultitrackPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalAppThemeColors provides MultitrackColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
