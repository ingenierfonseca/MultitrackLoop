package com.multitrack.player.presentation.screens.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitrack.player.R
import com.multitrack.player.presentation.theme.LocalAppThemeColors

@Composable
fun MultitrackDemoScreen(
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    val introLoop = stringResource(R.string.demo_loop_intro)
    val verseLoop = stringResource(R.string.demo_loop_verse)
    val chorusLoop = stringResource(R.string.demo_loop_chorus)
    var bpm by remember { mutableIntStateOf(120) }
    var selectedPad by remember { mutableStateOf("C") }
    var currentLoop by remember { mutableStateOf(introLoop) }
    var isPlaying by remember { mutableStateOf(false) }

    val notes = listOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = appColors.appBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppHeader()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                //verticalAlignment = Alignment.Stretch
            ) {
                // Canales de audio
                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackPanel(
                        name = stringResource(R.string.preview_track_drums),
                        level = 0.68f,
                        accent = appColors.accent,
                        modifier = Modifier.weight(1f)
                    )
                    TrackPanel(
                        name = stringResource(R.string.preview_track_bass),
                        level = 0.82f,
                        accent = appColors.accent,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Panel central de loops y pads
                LoopPanel(
                    currentLoop = currentLoop,
                    selectedPad = selectedPad,
                    notes = notes,
                    onLoopClick = {
                        currentLoop = when (currentLoop) {
                            introLoop -> verseLoop
                            verseLoop -> chorusLoop
                            else -> introLoop
                        }
                    },
                    onPadClick = { selectedPad = it },
                    modifier = Modifier
                        .weight(3.1f)
                        .fillMaxHeight()
                )

                // BPM y salida master
                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BpmPanel(
                        bpm = bpm,
                        onDecrease = { bpm = (bpm - 1).coerceAtLeast(40) },
                        onIncrease = { bpm = (bpm + 1).coerceAtMost(240) },
                        modifier = Modifier.weight(0.8f)
                    )

                    MasterPanel(
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }

            TransportBar(
                isPlaying = isPlaying,
                onPlay = { isPlaying = !isPlaying },
                onStop = { isPlaying = false },
                onLoop = {
                    currentLoop = if (currentLoop == introLoop) {
                        verseLoop
                    } else {
                        introLoop
                    }
                }
            )
        }
    }
}

@Composable
private fun AppHeader() {
    val appColors = LocalAppThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
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

        HeaderIcon(Icons.Default.FolderOpen, stringResource(R.string.cd_open_files))
        HeaderIcon(Icons.Default.MusicNote, stringResource(R.string.cd_loops))
        HeaderIcon(Icons.Default.Refresh, stringResource(R.string.cd_toggle_loop), appColors.accent)
        HeaderIcon(Icons.Default.Menu, stringResource(R.string.cd_menu))
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color? = null
) {
    val appColors = LocalAppThemeColors.current
    IconButton(onClick = { }) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint ?: appColors.mutedText,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun TrackPanel(
    name: String,
    level: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    var muted by remember { mutableStateOf(false) }
    var solo by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(level) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(15.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                name,
                color = appColors.mainText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AudioMeter(
                level = level,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(14.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(appColors.card),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(volume)
                        .background(
                            if (muted) appColors.mutedText.copy(alpha = 0.35f)
                            else accent
                        )
                )
            }
        }

        Slider(
            value = volume,
            onValueChange = { volume = it },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = appColors.border
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackButton(
                label = stringResource(R.string.demo_mute_short),
                active = muted,
                modifier = Modifier.weight(1f),
                onClick = { muted = !muted }
            )
            TrackButton(
                label = stringResource(R.string.demo_solo_short),
                active = solo,
                modifier = Modifier.weight(1f),
                onClick = { solo = !solo }
            )
        }
    }
}

@Composable
private fun AudioMeter(
    level: Float,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    val segments = 12
    val activeSegments = (level * segments).toInt()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(segments) { index ->
            val segmentIndex = segments - 1 - index
            val segmentColor = when {
                segmentIndex >= 9 -> appColors.meterRed
                segmentIndex >= 7 -> appColors.meterYellow
                else -> appColors.meterGreen
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (segmentIndex < activeSegments) segmentColor
                        else appColors.card
                    )
            )
        }
    }
}

@Composable
private fun TrackButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) appColors.accent.copy(alpha = 0.18f) else appColors.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) appColors.accent else appColors.mainText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LoopPanel(
    currentLoop: String,
    selectedPad: String,
    notes: List<String>,
    onLoopClick: () -> Unit,
    onPadClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(15.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onLoopClick,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.card)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.cd_toggle_loop),
                    tint = appColors.accent
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.card)
                    .clickable(onClick = onLoopClick)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    stringResource(R.string.demo_loop_current),
                    color = appColors.mutedText,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    currentLoop,
                    color = appColors.mainText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.card)
                    .clickable(onClick = onLoopClick)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    stringResource(R.string.demo_presets),
                    color = appColors.mutedText,
                    fontSize = 12.sp
                )
                Text(
                    stringResource(R.string.demo_loop_dropdown),
                    color = appColors.mainText,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Rejilla cromática de 12 pads
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            notes.chunked(6).forEach { rowNotes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowNotes.forEach { note ->
                        PadButton(
                            note = note,
                            selected = selectedPad == note,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onPadClick(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PadButton(
    note: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) appColors.accent else appColors.card)
            .border(
                width = 1.dp,
                color = if (selected) appColors.accent else appColors.border,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = note,
            color = if (selected) appColors.appBackground else appColors.mainText,
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun BpmPanel(
    bpm: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(15.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.bpm_label),
            color = appColors.mutedText,
            fontSize = 14.sp
        )

        Text(
            bpm.toString(),
            color = appColors.mainText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BpmButton(
                icon = Icons.Default.Remove,
                description = stringResource(R.string.cd_decrement_bpm),
                onClick = onDecrease,
                modifier = Modifier.weight(1f)
            )
            BpmButton(
                icon = Icons.Default.Add,
                description = stringResource(R.string.cd_increment_bpm),
                onClick = onIncrease,
                modifier = Modifier.weight(1f),
                highlighted = true
            )
        }
    }
}

@Composable
private fun BpmButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val appColors = LocalAppThemeColors.current
    IconButton(
        onClick = onClick,
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlighted) appColors.accent else appColors.card)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (highlighted) appColors.appBackground else appColors.mainText
        )
    }
}

@Composable
private fun MasterPanel(
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(appColors.panel)
            .border(1.dp, appColors.border, RoundedCornerShape(15.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            AudioMeter(
                level = 0.75f,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(14.dp)
            )

            Spacer(Modifier.width(12.dp))

            AudioMeter(
                level = 0.55f,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.demo_master),
            color = appColors.mutedText,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TransportBar(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onLoop: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledIconButton(
            onClick = onPlay,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = appColors.accent,
                contentColor = appColors.appBackground
            )
        ) {
            Icon(
                if (isPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play)
            )
        }

        FilledIconButton(
            onClick = onStop,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = appColors.card,
                contentColor = appColors.mainText
            )
        ) {
            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop))
        }

        FilledIconButton(
            onClick = onLoop,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = appColors.card,
                contentColor = appColors.accent
            )
        ) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_toggle_loop))
        }

        IconButton(onClick = { }) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                tint = appColors.mutedText
            )
        }

        Spacer(Modifier.width(6.dp))

        var progress by remember { mutableFloatStateOf(0.42f) }

        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = progress,
                onValueChange = { progress = it },
                colors = SliderDefaults.colors(
                    thumbColor = appColors.accent,
                    activeTrackColor = appColors.accent,
                    inactiveTrackColor = appColors.border
                )
            )
        }

        Text(
            stringResource(R.string.loop_label),
            color = if (isPlaying) appColors.accent else appColors.mutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Multitrack Landscape",
    widthDp = 900,
    heightDp = 420,
    showBackground = true
)
@Composable
private fun MultitrackScreenPreview() {
    MaterialTheme {
        MultitrackDemoScreen()
    }
}
