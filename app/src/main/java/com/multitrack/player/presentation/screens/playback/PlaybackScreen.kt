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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.multitrack.player.R
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackSession
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
                        onLoadProject = { projectPicker.launch(arrayOf("*/*")) }
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
    onLoadProject: () -> Unit
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
            onAddTrack = onAddTrack,
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
            TrackList(state.tracks, Modifier.weight(1f))
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
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = tracks, key = { it.id }) { track ->
            TrackRow(track.name)
        }
    }
}
@Composable
private fun TrackRow(name: String) {
    val appColors = LocalAppThemeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        VerticalLevelMeter(
            level = 100f,
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
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(15),
                    modifier = Modifier.rotate(-90f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = appColors.mainText
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.trackToggle)
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.track_enabled),
                    modifier = Modifier.padding(2.dp),
                    color = appColors.mainText
                )
            }
        }
    }
}

@Composable
fun VerticalLevelMeter(
    level: Float, // Valor entre 0.0f y 1.0f
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppThemeColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(appColors.meterBackground),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = level.coerceIn(0f, 1f))
                .background(MaterialTheme.colorScheme.secondary)
        )
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
                    onAddTrack = {},
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
