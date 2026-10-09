package com.multitrack.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.multitrack.player.R
import com.multitrack.player.presentation.theme.LocalAppThemeColors

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    isLoopEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val appColors = LocalAppThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isPlaying) {
            PrimaryControl(
                icon = Icons.Filled.Pause,
                onClick = onPause,
                contentDescription = stringResource(R.string.pause),
                testTag = "pause"
            )
        } else {
            PrimaryControl(
                icon = Icons.Filled.PlayArrow,
                onClick = onPlay,
                contentDescription = stringResource(R.string.play),
                testTag = "play"
            )
        }
        SecondaryControl(
            icon = Icons.Filled.Stop,
            contentDescription = stringResource(R.string.stop),
            onClick = onStop,
            testTag = "stop"
        )
        SecondaryControl(
            icon = Icons.Outlined.RestartAlt,
            contentDescription = stringResource(R.string.restart),
            onClick = onRestart,
            testTag = "restart"
        )
        SecondaryControl(
            icon = Icons.Outlined.Repeat,
            contentDescription = stringResource(R.string.cd_toggle_loop),
            onClick = { onToggle(!isLoopEnabled) },
            testTag = "repeat",
            active = isLoopEnabled
        )

        PlaybackProgress(positionMs = positionMs, durationMs = durationMs)
    }
}

@Composable
private fun PrimaryControl(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    testTag: String
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .semantics { this.testTag = testTag },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun SecondaryControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String,
    active: Boolean = false
) {
    val containerColor = getActiveBgColor(active)
    val contentColor = getActiveTintColor(active)
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .semantics { this.testTag = testTag },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

@Composable
fun getActiveBgColor(isActive: Boolean): Color {
    if (isActive) {
        return MaterialTheme.colorScheme.primary
    }
    return MaterialTheme.colorScheme.secondaryContainer
}

@Composable
fun getActiveTintColor(isActive: Boolean): Color {
    if (isActive) {
        return MaterialTheme.colorScheme.onPrimary
    }
    return MaterialTheme.colorScheme.onSecondaryContainer
}
