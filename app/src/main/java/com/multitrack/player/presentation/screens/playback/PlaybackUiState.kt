package com.multitrack.player.presentation.screens.playback

import com.multitrack.player.domain.model.AudioError
import com.multitrack.player.domain.model.AudioTrack
import com.multitrack.player.domain.model.PlaybackState

sealed interface PlaybackUiState {
    data object Loading : PlaybackUiState
    data class Ready(
        val tracks: List<AudioTrack>,
        val bpm: Int,
        val isPlaying: Boolean,
        val isPaused: Boolean,
        val isLoopEnabled: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val isLoading: Boolean = false,
        val error: AudioError?
    ) : PlaybackUiState

    data class Error(val audioError: AudioError) : PlaybackUiState
}

internal fun PlaybackState.toUi(): PlaybackUiState = if (error != null && tracks.isEmpty()) {
    PlaybackUiState.Error(error)
} else {
    PlaybackUiState.Ready(
        tracks = tracks,
        bpm = currentBpm,
        isPlaying = isPlaying,
        isPaused = isPaused,
        isLoopEnabled = isLoopEnabled,
        positionMs = positionMs,
        durationMs = durationMs,
        isLoading = isLoading,
        error = error
    )
}
