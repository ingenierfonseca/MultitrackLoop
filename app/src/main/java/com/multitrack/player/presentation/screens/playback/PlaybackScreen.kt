package com.multitrack.player.presentation.screens.playback

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multitrack.player.R
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.model.TrackMeterLevel
import com.multitrack.player.presentation.components.AppHeader
import com.multitrack.player.presentation.components.BpmControl
import com.multitrack.player.presentation.components.PlaybackControls
import com.multitrack.player.presentation.theme.LocalAppThemeColors

@Composable
fun PlaybackScreen(
    session: PlaybackSession,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var showProjectNameDialog by remember { mutableStateOf(false) }
    var projectNameInput by remember { mutableStateOf("") }
    var showLoopQuantizationWarning by remember { mutableStateOf(false) }
    var shortLoopTrackNames by remember { mutableStateOf(emptyList<String>()) }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistUriPermission(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = queryDisplayName(context, uri)
                ?: uri.lastPathSegment
                ?: context.getString(R.string.unknown_audio_name)
            viewModel.addTrack(uri.toString(), name, context.contentResolver.getType(uri))
        }
    }
    val createProjectDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            persistUriPermission(
                context,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.saveProjectAs(uri.toString(), projectNameInput)
        }
    }
    val projectPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistUriPermission(context, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.loadProject(uri.toString())
        }
    }

    SideEffect {
        activity?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    LaunchedEffect(session) { viewModel.bindSession(session) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val projectState by viewModel.projectUiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val s = state) {
                PlaybackUiState.Loading -> LoadingView()
                is PlaybackUiState.Error -> ErrorView(s)
                is PlaybackUiState.Ready -> Box(Modifier.fillMaxSize()) {
                    ReadyView(
                        state = s,
                        vm = viewModel,
                        projectState = projectState,
                        onAddTrack = { audioPicker.launch(arrayOf("audio/*")) },
                        onSaveProjectAs = {
                            projectNameInput = projectState.projectName.orEmpty()
                            showProjectNameDialog = true
                        },
                        onSaveProject = viewModel::saveProject,
                        onLoadProject = { projectPicker.launch(arrayOf("*/*")) },
                        onToggleLoopQuantization = {
                            if (s.isLoopQuantized) {
                                viewModel.onToggleLoopQuantized(false)
                            } else {
                                val fourBarsMs = 16L * 60_000L / s.bpm.coerceAtLeast(1)
                                val shortTracks = s.tracks.filter {
                                    it.durationMs == null || it.durationMs < fourBarsMs
                                }
                                if (shortTracks.isEmpty()) {
                                    viewModel.onToggleLoopQuantized(true)
                                } else {
                                    shortLoopTrackNames = shortTracks.map { it.name }
                                    showLoopQuantizationWarning = true
                                }
                            }
                        }
                    )
                    if (s.isLoading || projectState.isBusy) {
                        ProjectOperationOverlay(projectState.operation)
                    }
                }
            }
        }
    }

    if (showProjectNameDialog) {
        AlertDialog(
            onDismissRequest = { showProjectNameDialog = false },
            title = { Text(stringResource(R.string.project_name_title)) },
            text = {
                OutlinedTextField(
                    value = projectNameInput,
                    onValueChange = { projectNameInput = it },
                    label = { Text(stringResource(R.string.project_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = projectNameInput.isNotBlank(),
                    onClick = {
                        val safeName = projectNameInput.trim()
                            .replace(Regex("\\.mtl$", RegexOption.IGNORE_CASE), "")
                            .trim()
                        if (safeName.isNotEmpty()) {
                            projectNameInput = safeName
                            showProjectNameDialog = false
                            createProjectDocument.launch(projectFileName(safeName))
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProjectNameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showLoopQuantizationWarning) {
        AlertDialog(
            onDismissRequest = { showLoopQuantizationWarning = false },
            title = { Text(stringResource(R.string.loop_quantization_warning_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.loop_quantization_warning_message))
                    Text(shortLoopTrackNames.joinToString("\n"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLoopQuantizationWarning = false
                        viewModel.onToggleLoopQuantized(true)
                    }
                ) {
                    Text(stringResource(R.string.loop_quantization_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoopQuantizationWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun persistUriPermission(context: Context, uri: Uri, flags: Int) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
}

private fun queryDisplayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0) cursor.getString(nameColumn) else null
        } else null
    }

private fun projectFileName(name: String): String {
    val baseName = name.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\.mtl$", RegexOption.IGNORE_CASE), "")
        .trim()
    return "$baseName.mtl"
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ProjectOperationOverlay(operation: ProjectOperation?) {
    val appColors = LocalAppThemeColors.current
    val message = stringResource(
        when (operation) {
            ProjectOperation.ADDING_TRACK -> R.string.loading_track
            ProjectOperation.SAVING -> R.string.saving_project
            ProjectOperation.LOADING -> R.string.loading_project
            null -> R.string.loading
        }
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.appBackground.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = message,
                color = appColors.mainText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun ErrorView(state: PlaybackUiState.Error) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = state.audioError.toUserMessage(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

@Composable
private fun ReadyView(
    state: PlaybackUiState.Ready,
    vm: PlaybackViewModel,
    projectState: PlaybackProjectUiState,
    onAddTrack: () -> Unit,
    onSaveProjectAs: () -> Unit,
    onSaveProject: () -> Unit,
    onLoadProject: () -> Unit,
    onToggleLoopQuantization: () -> Unit
) {
    val appColors = LocalAppThemeColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AppHeader(
            isPlaying = state.isPlaying,
            isBusy = state.isLoading || projectState.isBusy,
            canSaveProject = projectState.canSave,
            isMetronomeEnabled = state.isMetronomeEnabled,
            isLoopQuantized = state.isLoopQuantized,
            onAddTrack = onAddTrack,
            onToggleMetronome = vm::onToggleMetronome,
            onToggleLoopQuantization = onToggleLoopQuantization,
            onSaveProjectAs = onSaveProjectAs,
            onSaveProject = onSaveProject,
            onLoadProject = onLoadProject
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            TrackList(
                tracks = state.tracks,
                modifier = Modifier.weight(1f),
                mutedTrackIds = state.mutedTrackIds,
                soloTrackIds = state.soloTrackIds,
                trackVolumes = state.trackVolumes,
                trackMeterLevels = state.trackMeterLevels,
                onToggleMute = vm::onToggleTrackMute,
                onToggleSolo = vm::onToggleTrackSolo,
                onVolumeChange = vm::onTrackVolumeChanged
            )
            Column(
                modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .fillMaxHeight()
                    .background(appColors.panel)
                    .border(1.dp, appColors.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.bpm_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                BpmControl(
                    bpm = state.bpm,
                    onDecrement = vm::onDecreaseBpm,
                    onIncrement = vm::onIncreaseBpm
                )
            }
        }
        PlaybackControls(
            isPlaying = state.isPlaying,
            durationMs = state.durationMs,
            positionMs = state.positionMs,
            onPlay = vm::onPlayPause,
            onPause = vm::onPlayPause,
            onStop = vm::onStop,
            onRestart = vm::onRestart,
            isLoopEnabled = state.isLoopEnabled,
            onToggle = vm::onToggleLoop
        )

        if (state.error != null) {
            Text(
                text = state.error.toUserMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = "playback-error" }
            )
        }
        if (projectState.error != null) {
            Text(
                text = if (projectState.error == "project_operation_failed") {
                    stringResource(R.string.project_operation_failed)
                } else {
                    projectState.error
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<AudioTrack>,
    modifier: Modifier = Modifier,
    mutedTrackIds: Set<String> = emptySet(),
    soloTrackIds: Set<String> = emptySet(),
    trackVolumes: Map<String, Float> = emptyMap(),
    trackMeterLevels: Map<String, TrackMeterLevel> = emptyMap(),
    onToggleMute: (String) -> Unit = {},
    onToggleSolo: (String) -> Unit = {},
    onVolumeChange: (String, Float) -> Unit = { _, _ -> }
) {
    LazyRow(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isMuted = track.id in mutedTrackIds,
                isSolo = track.id in soloTrackIds,
                volume = trackVolumes[track.id] ?: 1f,
                meterLevel = trackMeterLevels[track.id] ?: TrackMeterLevel.Silent,
                onToggleMute = { onToggleMute(track.id) },
                onToggleSolo = { onToggleSolo(track.id) },
                onVolumeChange = { onVolumeChange(track.id, it) }
            )
        }
    }
}
@Composable
private fun TrackRow(
    track: AudioTrack,
    isMuted: Boolean,
    isSolo: Boolean,
    volume: Float,
    meterLevel: TrackMeterLevel,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val appColors = LocalAppThemeColors.current
    val muteDescription = stringResource(
        if (isMuted) R.string.track_unmute_action else R.string.track_mute_action,
        track.name
    )
    val soloDescription = stringResource(
        if (isSolo) R.string.track_unsolo_action else R.string.track_solo_action,
        track.name
    )
    val volumeDescription = stringResource(R.string.track_volume_action, track.name)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VerticalLevelMeter(
            level = meterLevel,
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier.width(70.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(appColors.border)
                    .pointerInput(track.id) {
                        detectTapGestures { position ->
                            onVolumeChange((1f - position.y / size.height).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(track.id) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            onVolumeChange((1f - change.position.y / size.height).coerceIn(0f, 1f))
                        }
                    }
                    .semantics {
                        testTag = "track-volume-${track.id}"
                        contentDescription = volumeDescription
                        progressBarRangeInfo = ProgressBarRangeInfo(volume, 0f..1f)
                        setProgress { requestedVolume ->
                            onVolumeChange(requestedVolume.coerceIn(0f, 1f))
                            true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(volume.coerceIn(0f, 1f))
                        .align(Alignment.BottomCenter)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = track.name.take(15),
                    modifier = Modifier.rotate(-90f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = appColors.mainText
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isMuted) appColors.meterRed else appColors.trackToggle)
                    .height(30.dp)
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (isMuted) R.string.track_muted else R.string.track_enabled
                    ),
                    modifier = Modifier
                        .semantics {
                            testTag = "track-mute-${track.id}"
                            contentDescription = muteDescription
                            role = Role.Button
                        },
                    color = if (isMuted) Color.Black else appColors.mainText,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        if (isSolo) appColors.meterRed else appColors.card,
                    )
                    .clickable(onClick = onToggleSolo),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.track_solo),
                    color = if (isSolo) Color.Black else appColors.mutedText,
                    modifier = Modifier
                        .semantics {
                            testTag = "track-solo-${track.id}"
                            contentDescription = soloDescription
                            role = Role.Button
                        },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun VerticalLevelMeter(
    level: TrackMeterLevel,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    val rmsAnimation = remember { Animatable(0f) }
    val peakAnimation = remember { Animatable(0f) }

    LaunchedEffect(level.rms) {
        val target = level.rms.coerceIn(0f, 1f)
        rmsAnimation.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (target > rmsAnimation.value) 45 else 240,
                easing = LinearEasing
            )
        )
    }
    LaunchedEffect(level.peak) {
        peakAnimation.animateTo(
            targetValue = level.peak.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 35, easing = LinearEasing)
        )
    }

    Canvas(modifier = modifier.clip(RoundedCornerShape(3.dp))) {
        drawRect(color = appColors.meterBackground)

        // Keep the gradient anchored to the full meter, revealing it upward as
        // the RMS level increases (green -> yellow -> red).
        val gradient = Brush.verticalGradient(
            colors = listOf(appColors.meterRed, appColors.meterYellow, appColors.meterGreen),
            startY = 0f,
            endY = size.height
        )
        val fillHeight = size.height * rmsAnimation.value
        if (fillHeight > 0f) {
            drawRect(
                brush = gradient,
                topLeft = Offset(0f, size.height - fillHeight),
                size = Size(size.width, fillHeight)
            )
        }

        if (peakAnimation.value > 0f) {
            val peakY = size.height * (1f - peakAnimation.value)
            drawLine(
                color = appColors.mainText,
                start = Offset(0f, peakY),
                end = Offset(size.width, peakY),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun com.multitrack.player.domain.model.AudioError.toUserMessage(): String = when (this) {
    com.multitrack.player.domain.model.AudioError.UnsupportedFormat ->
        stringResource(R.string.error_unsupported_format)
    com.multitrack.player.domain.model.AudioError.FileNotFound ->
        stringResource(R.string.error_file_not_found)
    com.multitrack.player.domain.model.AudioError.PermissionDenied ->
        stringResource(R.string.error_permission_denied)
    is com.multitrack.player.domain.model.AudioError.InvalidAudio ->
        stringResource(R.string.error_invalid_audio)
    is com.multitrack.player.domain.model.AudioError.DecoderError ->
        stringResource(R.string.error_decoder_error)
    is com.multitrack.player.domain.model.AudioError.PlaybackError ->
        stringResource(R.string.error_playback_error)
    is com.multitrack.player.domain.model.AudioError.Unknown ->
        this.causeMessage ?: stringResource(R.string.error_generic)
}

@Preview(
    name = "Landscape",
    widthDp = 900,
    heightDp = 440,
    //device = "spec:width=992dp,height=444dp",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PlaybackScreenPreview() {
    val appColors = LocalAppThemeColors.current
    val tracks = listOf(
        AudioTrack("preview-1", stringResource(R.string.preview_track_drums), "preview://drums", 185_000L, com.multitrack.player.domain.model.AudioFormat.WAV),
        AudioTrack("preview-2", stringResource(R.string.preview_track_bass), "preview://bass", 185_000L, com.multitrack.player.domain.model.AudioFormat.WAV),
        AudioTrack("preview-3", stringResource(R.string.preview_track_guitar), "preview://rhythm-guitar", 185_000L, com.multitrack.player.domain.model.AudioFormat.MP3),
        AudioTrack("preview-4", stringResource(R.string.preview_track_keys), "preview://keys", 185_000L, com.multitrack.player.domain.model.AudioFormat.MP3),
        AudioTrack("preview-5", stringResource(R.string.preview_track_vocals), "preview://vocals", 185_000L, com.multitrack.player.domain.model.AudioFormat.WAV)
    )
    val bpm = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(120) }
    val isPlaying = androidx.compose.runtime.remember { mutableStateOf(false) }
    val isLoopEnabled = androidx.compose.runtime.remember { mutableStateOf(true) }
    val isMetronomeEnabled = androidx.compose.runtime.remember { mutableStateOf(false) }
    val isLoopQuantized = androidx.compose.runtime.remember { mutableStateOf(false) }
    val positionMs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(63_000L) }
    val durationMs = 185_000L

    LaunchedEffect(isPlaying.value) {
        while (isPlaying.value) {
            val nextPosition = positionMs.longValue + 100L
            if (nextPosition >= durationMs) {
                if (isLoopEnabled.value) {
                    positionMs.longValue = 0L
                } else {
                    positionMs.longValue = durationMs
                    isPlaying.value = false
                }
            } else {
                positionMs.longValue = nextPosition
            }
        }
    }

    Scaffold { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                AppHeader(
                    isPlaying = isPlaying.value,
                    isBusy = false,
                    canSaveProject = false,
                    isMetronomeEnabled = isMetronomeEnabled.value,
                    isLoopQuantized = isLoopQuantized.value,
                    onAddTrack = {},
                    onToggleMetronome = { isMetronomeEnabled.value = !isMetronomeEnabled.value },
                    onToggleLoopQuantization = { isLoopQuantized.value = !isLoopQuantized.value },
                    onSaveProjectAs = {},
                    onSaveProject = {},
                    onLoadProject = {}
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    TrackList(tracks, Modifier.weight(1f))
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .fillMaxHeight()
                            .background(appColors.panel)
                            .border(1.dp, appColors.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.bpm_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        BpmControl(
                            bpm = bpm.intValue,
                            onDecrement = { bpm.intValue = (bpm.intValue - 1).coerceAtLeast(40) },
                            onIncrement = { bpm.intValue = (bpm.intValue + 1).coerceAtMost(300) }
                        )
                    }
                }
                PlaybackControls(
                    isPlaying = isPlaying.value,
                    durationMs,
                    positionMs.longValue,
                    onPlay = { isPlaying.value = true },
                    onPause = { isPlaying.value = false },
                    onStop = {
                        isPlaying.value = false
                        positionMs.longValue = 0L
                    },
                    onRestart = { positionMs.longValue = 0L },
                    isLoopEnabled = isLoopEnabled.value,
                    onToggle = { isLoopEnabled.value = it }
                )
            }
        }
    }
}
