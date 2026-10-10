package com.multitrack.player.presentation.screens.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.multitrack.player.domain.model.PlaybackState
import kotlinx.coroutines.CancellationException
import com.multitrack.player.domain.model.PlaybackProject
import com.multitrack.player.domain.model.PlaybackSession
import com.multitrack.player.domain.repository.PlaybackEngine
import com.multitrack.player.domain.usecase.PlaybackControlUseCase
import com.multitrack.player.domain.usecase.LoadPlaybackProjectUseCase
import com.multitrack.player.domain.usecase.ResolvePickedTrackUseCase
import com.multitrack.player.domain.usecase.SavePlaybackProjectUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val engine: PlaybackEngine,
    private val control: PlaybackControlUseCase,
    private val resolvePickedTrack: ResolvePickedTrackUseCase,
    private val savePlaybackProject: SavePlaybackProjectUseCase,
    private val loadPlaybackProject: LoadPlaybackProjectUseCase
) : ViewModel() {

    val uiState: StateFlow<PlaybackUiState> = engine.state
        .map { it.toUi() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PlaybackUiState.Loading
        )

    private val _projectUiState = MutableStateFlow(PlaybackProjectUiState())
    val projectUiState: StateFlow<PlaybackProjectUiState> = _projectUiState.asStateFlow()

    suspend fun bindSession(session: PlaybackSession) {
        engine.prepare(session)
    }

    fun addTrack(uri: String, displayName: String, mime: String?) {
        addTracks(listOf(PickedAudioFile(uri, displayName, mime)))
    }

    fun addTracks(files: List<PickedAudioFile>) {
        if (files.isEmpty() || engine.state.value.isPlaying || engine.state.value.isLoading || _projectUiState.value.isBusy) return
        viewModelScope.launch {
            _projectUiState.update {
                it.copy(isBusy = true, operation = ProjectOperation.ADDING_TRACK, error = null)
            }
            try {
                val resolvedTracks = files.mapNotNull { file ->
                    resolvePickedTrack(file.uri, file.displayName, file.mimeType).getOrNull()
                }
                require(resolvedTracks.isNotEmpty()) { "No supported audio tracks were selected" }
                val current = engine.state.value
                engine.prepare(
                    PlaybackSession(
                        tracks = current.tracks + resolvedTracks,
                        initialBpm = current.currentBpm,
                        originalBpm = current.originalBpm,
                        isLoopEnabled = current.isLoopEnabled,
                        isLoopQuantized = current.isLoopQuantized,
                        isMetronomeEnabled = current.isMetronomeEnabled,
                        mutedTrackIds = current.mutedTrackIds,
                        soloTrackIds = current.soloTrackIds,
                        trackVolumes = current.trackVolumes
                    )
                ).getOrThrow()
                _projectUiState.update {
                    it.copy(isBusy = false, operation = null, error = null)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _projectUiState.update {
                    it.copy(
                        isBusy = false,
                        operation = null,
                        error = "project_operation_failed"
                    )
                }
            }
        }
    }

    fun createNewProject(originalBpm: Int) {
        if (engine.state.value.isPlaying || engine.state.value.isLoading || _projectUiState.value.isBusy) return
        val bpm = originalBpm.coerceIn(PlaybackState.MIN_BPM, PlaybackState.MAX_BPM)
        viewModelScope.launch {
            _projectUiState.update {
                it.copy(isBusy = true, operation = ProjectOperation.CREATING_PROJECT, error = null)
            }
            try {
                engine.prepare(
                    PlaybackSession(
                        tracks = emptyList(),
                        initialBpm = bpm,
                        originalBpm = bpm
                    )
                ).getOrThrow()
                _projectUiState.value = PlaybackProjectUiState()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _projectUiState.update {
                    it.copy(
                        savedUri = null,
                        projectName = null,
                        isBusy = false,
                        operation = null,
                        error = "project_operation_failed"
                    )
                }
            }
        }
    }

    fun saveProjectAs(destinationUri: String, projectName: String) {
        saveProject(destinationUri, projectName)
    }

    fun saveProject() {
        val current = _projectUiState.value
        val uri = current.savedUri ?: return
        val name = current.projectName ?: return
        saveProject(uri, name)
    }

    private fun saveProject(destinationUri: String, projectName: String) {
        if (engine.state.value.isPlaying || engine.state.value.isLoading || _projectUiState.value.isBusy) return
        viewModelScope.launch {
            _projectUiState.update {
                it.copy(isBusy = true, operation = ProjectOperation.SAVING, error = null)
            }
            try {
                val playback = engine.state.value
                savePlaybackProject(
                    PlaybackProject(
                        name = projectName,
                        tracks = playback.tracks,
                        bpm = playback.currentBpm,
                        originalBpm = playback.originalBpm,
                        isLoopEnabled = playback.isLoopEnabled,
                        isLoopQuantized = playback.isLoopQuantized,
                        isMetronomeEnabled = playback.isMetronomeEnabled,
                        mutedTrackIds = playback.mutedTrackIds,
                        soloTrackIds = playback.soloTrackIds,
                        trackVolumes = playback.trackVolumes
                    ),
                    destinationUri
                )
                _projectUiState.update {
                    it.copy(
                        savedUri = destinationUri,
                        projectName = projectName,
                        isBusy = false,
                        operation = null,
                        error = null
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _projectUiState.update {
                    it.copy(
                        isBusy = false,
                        operation = null,
                        error = "project_operation_failed"
                    )
                }
            }
        }
    }

    fun loadProject(sourceUri: String) {
        if (engine.state.value.isPlaying || engine.state.value.isLoading || _projectUiState.value.isBusy) return
        viewModelScope.launch {
            _projectUiState.update {
                it.copy(isBusy = true, operation = ProjectOperation.LOADING, error = null)
            }
            try {
                val project = loadPlaybackProject(sourceUri)
                engine.prepare(
                        PlaybackSession(
                            tracks = project.tracks,
                            initialBpm = project.bpm,
                            originalBpm = project.originalBpm,
                            isLoopEnabled = project.isLoopEnabled,
                            isLoopQuantized = project.isLoopQuantized,
                            isMetronomeEnabled = project.isMetronomeEnabled,
                            mutedTrackIds = project.mutedTrackIds,
                            soloTrackIds = project.soloTrackIds,
                            trackVolumes = project.trackVolumes
                        )
                    ).getOrThrow()
                _projectUiState.update {
                    it.copy(
                        savedUri = sourceUri,
                        projectName = project.name,
                        isBusy = false,
                        operation = null,
                        error = null
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _projectUiState.update {
                    it.copy(
                        isBusy = false,
                        operation = null,
                        error = "project_operation_failed"
                    )
                }
            }
        }
    }

    fun clearProjectError() {
        _projectUiState.update { it.copy(error = null) }
    }

    fun onPlayPause() {
        val current = engine.state.value
        if (current.isLoading) return
        if (current.isPlaying) control.pause() else control.play()
    }

    fun onStop() {
        if (!engine.state.value.isLoading) control.stop()
    }

    fun onRestart() {
        if (!engine.state.value.isLoading) control.seekToStart()
    }

    fun onToggleLoop(enabled: Boolean) {
        if (!engine.state.value.isLoading) control.setLoop(enabled)
    }

    fun onToggleLoopQuantized(enabled: Boolean) {
        if (!engine.state.value.isLoading) control.setLoopQuantized(enabled)
    }

    fun onToggleTrackMute(trackId: String) {
        val current = engine.state.value
        if (current.isLoading || current.tracks.none { it.id == trackId }) return
        control.setTrackMuted(trackId, trackId !in current.mutedTrackIds)
    }

    fun onTrackVolumeChanged(trackId: String, volume: Float) {
        if (engine.state.value.isLoading) return
        control.setTrackVolume(trackId, volume)
    }

    fun onToggleTrackSolo(trackId: String) {
        val current = engine.state.value
        if (current.isLoading || current.tracks.none { it.id == trackId }) return
        control.setTrackSolo(trackId, trackId !in current.soloTrackIds)
    }

    fun onToggleMetronome() {
        val current = engine.state.value
        if (current.isLoading) return
        control.setMetronomeEnabled(!current.isMetronomeEnabled)
    }

    fun applyCountInSettings(enabled: Boolean, beats: Int) {
        control.setCountInSettings(enabled, beats.coerceIn(1, 4))
    }

    fun onIncreaseBpm() {
        if (engine.state.value.isLoading || engine.state.value.isCountingIn) return
        control.increaseBpm(engine.state.value.currentBpm)
    }

    fun onDecreaseBpm() {
        if (engine.state.value.isLoading || engine.state.value.isCountingIn) return
        control.decreaseBpm(engine.state.value.currentBpm)
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}

data class PlaybackProjectUiState(
    val savedUri: String? = null,
    val projectName: String? = null,
    val isBusy: Boolean = false,
    val operation: ProjectOperation? = null,
    val error: String? = null
) {
    val canSave: Boolean get() = savedUri != null
}

enum class ProjectOperation {
    ADDING_TRACK,
    SAVING,
    LOADING,
    CREATING_PROJECT
}

data class PickedAudioFile(
    val uri: String,
    val displayName: String,
    val mimeType: String?
)
