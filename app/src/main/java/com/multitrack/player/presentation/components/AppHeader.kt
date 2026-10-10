package com.multitrack.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitrack.player.R
import com.multitrack.player.presentation.theme.LocalAppThemeColors

@Composable
fun AppHeader(
    isPlaying: Boolean,
    isBusy: Boolean,
    canSaveProject: Boolean,
    isMetronomeEnabled: Boolean,
    onAddTrack: () -> Unit,
    onToggleMetronome: () -> Unit,
    onSaveProjectAs: () -> Unit,
    onSaveProject: () -> Unit,
    onLoadProject: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = appColors.accent,
            modifier = Modifier.size(32.dp)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = stringResource(R.string.multitrack_title),
            color = appColors.mainText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.multitrack_title2),
            color = appColors.mutedText,
            fontSize = 22.sp
        )

        Spacer(Modifier.weight(1f))

        HeaderIcon(
            icon = Icons.Default.FolderOpen,
            contentDescriptionRes = R.string.cd_open_files,
            enabled = !isPlaying && !isBusy,
            onClick = onAddTrack
        )
        MetronomeHeaderIcon(
            isEnabled = isMetronomeEnabled,
            enabled = !isBusy,
            onClick = onToggleMetronome
        )
        androidx.compose.foundation.layout.Box {
            HeaderIcon(
                icon = Icons.Default.Menu,
                contentDescriptionRes = R.string.cd_menu,
                onClick = { menuExpanded = true }
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.project_save_as)) },
                    enabled = !isPlaying && !isBusy,
                    onClick = {
                        menuExpanded = false
                        onSaveProjectAs()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.project_save)) },
                    enabled = canSaveProject && !isPlaying && !isBusy,
                    onClick = {
                        menuExpanded = false
                        onSaveProject()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.project_load)) },
                    enabled = !isPlaying && !isBusy,
                    onClick = {
                        menuExpanded = false
                        onLoadProject()
                    }
                )
            }
        }
    }
}

@Composable
private fun MetronomeHeaderIcon(
    isEnabled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    val description = stringResource(R.string.cd_toggle_metronome)
    val tint = if (isEnabled) appColors.accent else appColors.mutedText
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Canvas(Modifier.size(23.dp)) {
            val strokeWidth = 2.dp.toPx()
            val left = size.width * 0.22f
            val right = size.width * 0.78f
            val top = size.height * 0.12f
            val bottom = size.height * 0.88f
            drawLine(tint, Offset(left, bottom), Offset(size.width * 0.44f, top), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, Offset(right, bottom), Offset(size.width * 0.56f, top), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, Offset(left, bottom), Offset(right, bottom), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.44f, top), Offset(size.width * 0.56f, top), strokeWidth, cap = StrokeCap.Round)
            drawLine(tint, Offset(size.width * 0.52f, size.height * 0.22f), Offset(size.width * 0.34f, size.height * 0.68f), strokeWidth, cap = StrokeCap.Round)
            drawCircle(tint, radius = strokeWidth * 1.8f, center = Offset(size.width * 0.34f, size.height * 0.68f))
            drawLine(tint, Offset(size.width * 0.58f, size.height * 0.34f), Offset(size.width * 0.68f, size.height * 0.34f), strokeWidth = strokeWidth * 0.7f)
            drawLine(tint, Offset(size.width * 0.61f, size.height * 0.45f), Offset(size.width * 0.7f, size.height * 0.45f), strokeWidth = strokeWidth * 0.7f)
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescriptionRes: Int,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = tint ?: appColors.mutedText,
            modifier = Modifier.size(23.dp)
        )
    }
}
