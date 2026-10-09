package com.multitrack.player.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

@Composable
fun PlaybackProgress(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = remember(positionMs, durationMs) {
        derivedStateOf {
            if (durationMs <= 0L) 0f
            else (positionMs.coerceAtLeast(0L).coerceAtMost(durationMs).toFloat() / durationMs.toFloat())
        }
    }
    LinearProgressIndicator(
        progress = { progress.value },
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = "playback-progress" },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap
    )
}
